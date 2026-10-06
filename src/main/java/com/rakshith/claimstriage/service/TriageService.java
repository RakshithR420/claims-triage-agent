package com.rakshith.claimstriage.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rakshith.claimstriage.agent.*;
import com.rakshith.claimstriage.agent.GuardrailService.GuardedDecision;
import com.rakshith.claimstriage.agent.tools.ClaimAnalyzer;
import com.rakshith.claimstriage.config.TriageProperties;
import com.rakshith.claimstriage.domain.*;
import com.rakshith.claimstriage.repository.AgentStepRepository;
import com.rakshith.claimstriage.repository.ClaimRepository;
import com.rakshith.claimstriage.repository.PolicyRepository;
import com.rakshith.claimstriage.repository.TriageDecisionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * Orchestrates one triage run:
 * <pre>
 *  claim -> toolbox (bound to this claim) -> decision engine (LLM, or rules)
 *        -> on LLM error/timeout: rules engine fallback
 *        -> guardrails (hard business rules) -> save decision + audit trail -> update claim status
 * </pre>
 */
@Service
public class TriageService {

    private static final Logger log = LoggerFactory.getLogger(TriageService.class);

    /** Result of a triage run before it is persisted (also used by the eval harness). */
    public record TriageOutcome(GuardedDecision guarded, DecisionSource source, String model,
                                long latencyMs, AgentTrace trace) {
    }

    private final ClaimRepository claims;
    private final PolicyRepository policies;
    private final TriageDecisionRepository decisions;
    private final AgentStepRepository steps;
    private final ClaimAnalyzer analyzer;
    private final RulesDecisionEngine rulesEngine;
    private final LlmDecisionEngine llmEngine; // null when triage.llm.enabled=false
    private final GuardrailService guardrails;
    private final TriageProperties properties;
    private final ObjectMapper json;
    private final TransactionTemplate tx;
    private final ExecutorService llmExecutor = Executors.newVirtualThreadPerTaskExecutor();

    public TriageService(ClaimRepository claims, PolicyRepository policies, TriageDecisionRepository decisions,
                         AgentStepRepository steps, ClaimAnalyzer analyzer, RulesDecisionEngine rulesEngine,
                         ObjectProvider<LlmDecisionEngine> llmEngine, GuardrailService guardrails,
                         TriageProperties properties, ObjectMapper json, PlatformTransactionManager txManager) {
        this.claims = claims;
        this.policies = policies;
        this.decisions = decisions;
        this.steps = steps;
        this.analyzer = analyzer;
        this.rulesEngine = rulesEngine;
        this.llmEngine = llmEngine.getIfAvailable();
        this.guardrails = guardrails;
        this.properties = properties;
        this.json = json;
        this.tx = new TransactionTemplate(txManager);
    }

    /**
     * Triage a saved claim and persist the decision, the audit trail and the new claim status.
     * The (slow) LLM call runs outside any DB transaction; only the writes are transactional.
     */
    public TriageDecision triage(Long claimId) {
        Claim claim = claims.findById(claimId).orElseThrow(() -> new NotFoundException("Claim " + claimId + " not found"));
        TriageOutcome outcome = evaluate(claim);
        return tx.execute(status -> persist(claim, outcome));
    }

    private TriageDecision persist(Claim claim, TriageOutcome outcome) {
        TriageDraft d = outcome.guarded().decision();

        TriageDecision saved = decisions.save(new TriageDecision(
                claim.getId(), d.severity(), d.coverageStatus(), d.fraudRiskScore(), d.queue(), d.confidence(),
                d.summary(), String.join("\n", d.reasons()), String.join("\n", outcome.guarded().notes()),
                outcome.source(), outcome.model(), outcome.latencyMs(), outcome.guarded().humanReviewRequired(), null));

        steps.saveAll(outcome.trace().steps().stream()
                .map(s -> new AgentStep(claim.getId(), outcome.trace().runId(), s.stepNo(), s.toolName(),
                        s.input(), s.output(), s.at()))
                .toList());

        claim.setStatus(outcome.guarded().humanReviewRequired() ? ClaimStatus.PENDING_HUMAN_REVIEW : ClaimStatus.TRIAGED);
        claims.save(claim);
        log.info("Claim {} -> {} (source={}, fraud={}, severity={}, {} ms, guardrail notes={})",
                claim.getId(), d.queue(), outcome.source(), d.fraudRiskScore(), d.severity(),
                outcome.latencyMs(), outcome.guarded().notes().size());
        return saved;
    }

    /** Run the agent + guardrails without saving anything. Works for unsaved (eval) claims too. */
    public TriageOutcome evaluate(Claim claim) {
        long start = System.nanoTime();
        AgentTrace trace = new AgentTrace();
        TriageToolbox toolbox = new TriageToolbox(
                claim,
                number -> policies.findById(number).orElse(null),
                claims.findByPolicyNumber(claim.getPolicyNumber()),
                analyzer, trace, json);

        List<String> extraNotes = new ArrayList<>();
        DecisionEngine engine = llmEngine != null ? llmEngine : rulesEngine;
        TriageDraft draft;
        try {
            draft = engine == llmEngine ? callLlmWithTimeout(toolbox) : rulesEngine.decide(toolbox);
        } catch (Exception e) {
            String reason = rootMessage(e);
            log.warn("LLM triage failed for claim {} ({}); falling back to rules engine", claim.getId(), reason);
            extraNotes.add("LLM agent failed (" + reason + "); decision made by rules engine fallback");
            engine = rulesEngine;
            draft = rulesEngine.decide(toolbox);
        }

        GuardedDecision guarded = guardrails.apply(draft, toolbox.facts(), claim.getClaimedAmount());
        if (!extraNotes.isEmpty()) {
            List<String> notes = new ArrayList<>(extraNotes);
            notes.addAll(guarded.notes());
            guarded = new GuardedDecision(guarded.decision(), List.copyOf(notes), guarded.humanReviewRequired());
        }
        long latencyMs = (System.nanoTime() - start) / 1_000_000;
        return new TriageOutcome(guarded, engine.source(), engine.modelName(), latencyMs, trace);
    }

    public boolean llmEnabled() {
        return llmEngine != null;
    }

    private TriageDraft callLlmWithTimeout(TriageToolbox toolbox) throws Exception {
        Future<TriageDraft> future = llmExecutor.submit(() -> llmEngine.decide(toolbox));
        try {
            return future.get(properties.llm().timeoutSeconds(), TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new TimeoutException("LLM did not answer within " + properties.llm().timeoutSeconds() + "s");
        } catch (ExecutionException e) {
            throw e.getCause() instanceof Exception ex ? ex : e;
        }
    }

    private static String rootMessage(Throwable e) {
        Throwable t = e;
        while (t.getCause() != null && t.getCause() != t) {
            t = t.getCause();
        }
        String msg = t.getMessage();
        return t.getClass().getSimpleName() + (msg == null ? "" : ": " + msg);
    }
}
