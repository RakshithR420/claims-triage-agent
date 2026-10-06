package com.rakshith.claimstriage.eval;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rakshith.claimstriage.domain.Claim;
import com.rakshith.claimstriage.domain.DecisionSource;
import com.rakshith.claimstriage.domain.LossType;
import com.rakshith.claimstriage.domain.RoutingQueue;
import com.rakshith.claimstriage.service.TriageService;
import com.rakshith.claimstriage.service.TriageService.TriageOutcome;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/**
 * Offline evaluation: runs every labelled claim in {@code eval/claims-eval.json} through the full
 * pipeline (agent + guardrails) without saving anything, and reports routing accuracy.
 * <p>
 * "Under-routed" = the claim landed in a less escalated queue than the label (the dangerous kind of
 * mistake, e.g. fast-tracking something that needed an investigator). Guardrails should keep it at 0.
 */
@Service
public class EvalService {

    public record EvalCase(String id, String policyNumber, LossType lossType, int lossDaysAgo, int reportedDaysAgo,
                           BigDecimal amount, String description, RoutingQueue expectedQueue) {
    }

    public record CaseResult(String id, RoutingQueue expected, RoutingQueue actual, boolean correct,
                             boolean underRouted, int fraudScore, String severity, DecisionSource source,
                             long latencyMs, List<String> guardrailNotes, String summary) {
    }

    public record EvalReport(String engine, int total, int correct, double accuracy, int underRouted,
                             int guardrailInterventions, int llmFallbacks, long avgLatencyMs,
                             Map<RoutingQueue, Map<RoutingQueue, Integer>> confusion, List<CaseResult> cases) {
    }

    private final TriageService triage;
    private final ObjectMapper json;

    public EvalService(TriageService triage, ObjectMapper json) {
        this.triage = triage;
        this.json = json;
    }

    public List<EvalCase> loadCases() {
        try (InputStream in = new ClassPathResource("eval/claims-eval.json").getInputStream()) {
            return json.readValue(in, new TypeReference<List<EvalCase>>() {
            });
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read eval dataset", e);
        }
    }

    public EvalReport run() {
        List<EvalCase> cases = loadCases();
        LocalDate today = LocalDate.now();
        List<CaseResult> results = new ArrayList<>();
        Map<RoutingQueue, Map<RoutingQueue, Integer>> confusion = new EnumMap<>(RoutingQueue.class);
        int correct = 0, under = 0, interventions = 0, fallbacks = 0;
        long totalLatency = 0;
        String engine = null;

        for (EvalCase c : cases) {
            Claim claim = new Claim(c.policyNumber(), "Eval " + c.id(), c.lossType(),
                    today.minusDays(c.lossDaysAgo()), today.minusDays(c.reportedDaysAgo()), c.amount(), c.description());
            TriageOutcome out = triage.evaluate(claim);
            var d = out.guarded().decision();

            boolean ok = d.queue() == c.expectedQueue();
            boolean underRouted = isUnderRouted(c.expectedQueue(), d.queue());
            List<String> notes = out.guarded().notes();
            boolean fellBack = notes.stream().anyMatch(n -> n.startsWith("LLM agent failed"));

            if (ok) correct++;
            if (underRouted) under++;
            if (notes.stream().anyMatch(n -> !n.startsWith("LLM agent failed"))) interventions++;
            if (fellBack) fallbacks++;
            totalLatency += out.latencyMs();
            if (engine == null || !fellBack) engine = out.source() + " (" + out.model() + ")";

            confusion.computeIfAbsent(c.expectedQueue(), k -> new EnumMap<>(RoutingQueue.class))
                    .merge(d.queue(), 1, Integer::sum);
            results.add(new CaseResult(c.id(), c.expectedQueue(), d.queue(), ok, underRouted, d.fraudRiskScore(),
                    String.valueOf(d.severity()), out.source(), out.latencyMs(), notes, d.summary()));
        }

        int total = cases.size();
        return new EvalReport(engine, total, correct, total == 0 ? 0 : (double) correct / total, under,
                interventions, fallbacks, total == 0 ? 0 : totalLatency / total, confusion, results);
    }

    static boolean isUnderRouted(RoutingQueue expected, RoutingQueue actual) {
        if (expected == RoutingQueue.DENIAL_REVIEW) {
            return actual != RoutingQueue.DENIAL_REVIEW;
        }
        if (actual == RoutingQueue.DENIAL_REVIEW) {
            return false; // sent to a human anyway
        }
        return actual.escalationLevel() < expected.escalationLevel();
    }
}
