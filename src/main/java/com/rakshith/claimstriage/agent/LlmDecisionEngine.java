package com.rakshith.claimstriage.agent;

import com.rakshith.claimstriage.domain.Claim;
import com.rakshith.claimstriage.domain.DecisionSource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * LLM agent (Spring AI + Ollama). The model is given the claim, decides which tools to call
 * (Spring AI runs the tool-calling loop), then returns a {@link TriageDraft} as structured JSON.
 * <p>
 * Its output is a <i>proposal</i>: {@link GuardrailService} checks it against the deterministic
 * facts before anything is saved.
 */
@Component
@ConditionalOnProperty(prefix = "triage.llm", name = "enabled", havingValue = "true")
public class LlmDecisionEngine implements DecisionEngine {

    // Note: no curly braces in prompts - Spring AI treats them as template variables.
    static final String SYSTEM_PROMPT = """
            You are a first-notice-of-loss (FNOL) triage agent for an insurance company.
            Your job is to decide which work queue a new claim goes to, and to write a short
            summary for the adjuster who will pick it up.

            How to work:
            1. Call lookupPolicy with the claim's policy number.
            2. Call checkCoverage, getClaimHistory, assessFraudSignals and estimateSeverity.
            3. Read the loss description yourself as well. You may raise severity or fraud risk
               above what the tools say if the description justifies it (for example injuries,
               inconsistent story, pressure for quick cash payment). Explain why in the reasons.
            4. Never invent facts. Only use the claim details and tool results.

            Routing policy:
            - DENIAL_REVIEW: coverage is not COVERED (not covered, policy inactive, or unknown).
            - SPECIAL_INVESTIGATIONS: fraud risk 70 or higher.
            - SENIOR_ADJUSTER: fraud risk 40 or higher, or severity HIGH or CATASTROPHIC.
            - FAST_TRACK: severity LOW, fraud risk below 30 and claimed amount at most 5000.
            - STANDARD_ADJUSTER: everything else.

            Set confidence below 0.6 if the description is vague, contradictory or missing key details.
            Reply only with the JSON object requested. No extra commentary.
            """;

    private final ChatClient chatClient;
    private final String modelName;

    public LlmDecisionEngine(ChatClient.Builder builder,
                             @Value("${spring.ai.ollama.chat.options.model:unknown}") String modelName) {
        this.chatClient = builder.defaultSystem(SYSTEM_PROMPT).build();
        this.modelName = modelName;
    }

    @Override
    public TriageDraft decide(TriageToolbox toolbox) {
        TriageDraft draft = chatClient.prompt()
                .user(describe(toolbox.claim()))
                .tools(toolbox)
                .call()
                .entity(TriageDraft.class);
        if (draft == null || draft.queue() == null) {
            throw new IllegalStateException("LLM returned no usable triage decision");
        }
        return draft;
    }

    static String describe(Claim claim) {
        return """
                Triage this new claim.

                Policy number: %s
                Claimant: %s
                Loss type: %s
                Loss date: %s
                Reported date: %s
                Claimed amount: %s
                Description: %s
                """.formatted(
                claim.getPolicyNumber(),
                sanitize(claim.getClaimantName()),
                claim.getLossType(),
                claim.getLossDate(),
                claim.getReportedDate(),
                claim.getClaimedAmount(),
                sanitize(claim.getDescription()));
    }

    /** Claim text is untrusted user input: strip template delimiters before it reaches the prompt. */
    static String sanitize(String s) {
        if (s == null) return "";
        return s.replace('{', '(').replace('}', ')').replace("<", "(").replace(">", ")");
    }

    @Override
    public DecisionSource source() {
        return DecisionSource.LLM_AGENT;
    }

    @Override
    public String modelName() {
        return modelName;
    }
}
