package com.rakshith.claimstriage.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

/** Bound from the {@code triage.*} section of application.yml. */
@ConfigurationProperties(prefix = "triage")
public record TriageProperties(Llm llm, Guardrails guardrails) {

    public TriageProperties {
        if (llm == null) llm = new Llm(false, 90);
        if (guardrails == null) guardrails = new Guardrails(new BigDecimal("5000"), 70, 0.6);
    }

    public record Llm(boolean enabled, int timeoutSeconds) {
    }

    /**
     * @param fastTrackMaxAmount largest claim that may skip a human adjuster
     * @param siuFraudThreshold  fraud score at or above which a claim must go to Special Investigations
     * @param minConfidence      agent confidence below which a human must review the routing
     */
    public record Guardrails(BigDecimal fastTrackMaxAmount, int siuFraudThreshold, double minConfidence) {
    }
}
