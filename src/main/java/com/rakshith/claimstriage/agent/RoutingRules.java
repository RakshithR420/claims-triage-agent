package com.rakshith.claimstriage.agent;

import com.rakshith.claimstriage.agent.tools.ToolResults.ClaimFacts;
import com.rakshith.claimstriage.config.TriageProperties;
import com.rakshith.claimstriage.domain.CoverageStatus;
import com.rakshith.claimstriage.domain.RoutingQueue;
import com.rakshith.claimstriage.domain.Severity;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * The hard business rules for routing. Used two ways:
 * <ul>
 *   <li>by the rules engine, as the whole decision;</li>
 *   <li>by the guardrails, as the <b>minimum</b> queue an LLM decision is allowed to land in.
 *       The LLM may escalate a claim further, but never route it below this floor.</li>
 * </ul>
 */
@Component
public class RoutingRules {

    static final int SENIOR_FRAUD_THRESHOLD = 40;
    static final int FAST_TRACK_MAX_FRAUD = 30;

    private final TriageProperties.Guardrails limits;

    public RoutingRules(TriageProperties properties) {
        this.limits = properties.guardrails();
    }

    public RoutingQueue minimumQueue(CoverageStatus coverage, int fraudScore, Severity severity, BigDecimal amount) {
        if (coverage != CoverageStatus.COVERED) {
            return RoutingQueue.DENIAL_REVIEW;
        }
        if (fraudScore >= limits.siuFraudThreshold()) {
            return RoutingQueue.SPECIAL_INVESTIGATIONS;
        }
        if (fraudScore >= SENIOR_FRAUD_THRESHOLD || severity.atLeast(Severity.HIGH)) {
            return RoutingQueue.SENIOR_ADJUSTER;
        }
        if (isFastTrackEligible(fraudScore, severity, amount)) {
            return RoutingQueue.FAST_TRACK;
        }
        return RoutingQueue.STANDARD_ADJUSTER;
    }

    public RoutingQueue minimumQueue(ClaimFacts facts, BigDecimal amount) {
        return minimumQueue(facts.coverage().status(), facts.fraud().score(), facts.severity().severity(), amount);
    }

    public boolean isFastTrackEligible(int fraudScore, Severity severity, BigDecimal amount) {
        return severity == Severity.LOW
                && fraudScore < FAST_TRACK_MAX_FRAUD
                && amount.compareTo(limits.fastTrackMaxAmount()) <= 0;
    }

    public int siuThreshold() {
        return limits.siuFraudThreshold();
    }

    public double minConfidence() {
        return limits.minConfidence();
    }

    public BigDecimal fastTrackMaxAmount() {
        return limits.fastTrackMaxAmount();
    }
}
