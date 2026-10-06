package com.rakshith.claimstriage;

import com.rakshith.claimstriage.agent.GuardrailService;
import com.rakshith.claimstriage.agent.GuardrailService.GuardedDecision;
import com.rakshith.claimstriage.agent.RoutingRules;
import com.rakshith.claimstriage.agent.TriageDraft;
import com.rakshith.claimstriage.agent.tools.ToolResults.*;
import com.rakshith.claimstriage.config.TriageProperties;
import com.rakshith.claimstriage.domain.CoverageStatus;
import com.rakshith.claimstriage.domain.RoutingQueue;
import com.rakshith.claimstriage.domain.Severity;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** The guardrails must catch an LLM that under-routes a claim, whatever the model says. */
class GuardrailServiceTest {

    private final GuardrailService guardrails = new GuardrailService(new RoutingRules(new TriageProperties(null, null)));

    private static ClaimFacts facts(CoverageStatus coverage, int fraud, Severity severity) {
        return new ClaimFacts(null,
                new CoverageCheck(coverage, List.of(), null, null, BigDecimal.ZERO),
                new ClaimHistory(0, 0, BigDecimal.ZERO),
                new FraudAssessment(fraud, List.of()),
                new SeverityEstimate(severity, List.of()));
    }

    private static TriageDraft draft(RoutingQueue queue, CoverageStatus coverage, int fraud, Severity severity, double confidence) {
        return new TriageDraft(severity, coverage, fraud, queue, confidence, "summary", List.of("reason"));
    }

    @Test
    void cleanFastTrackDecisionPassesUntouched() {
        GuardedDecision g = guardrails.apply(
                draft(RoutingQueue.FAST_TRACK, CoverageStatus.COVERED, 5, Severity.LOW, 0.9),
                facts(CoverageStatus.COVERED, 0, Severity.LOW), new BigDecimal("1200"));
        assertEquals(RoutingQueue.FAST_TRACK, g.decision().queue());
        assertTrue(g.notes().isEmpty());
        assertFalse(g.humanReviewRequired());
    }

    @Test
    void llmCannotFastTrackAHighFraudClaim() {
        GuardedDecision g = guardrails.apply(
                draft(RoutingQueue.FAST_TRACK, CoverageStatus.COVERED, 10, Severity.LOW, 0.95),
                facts(CoverageStatus.COVERED, 80, Severity.LOW), new BigDecimal("2000"));
        assertEquals(RoutingQueue.SPECIAL_INVESTIGATIONS, g.decision().queue());
        assertEquals(80, g.decision().fraudRiskScore());
        assertTrue(g.humanReviewRequired());
    }

    @Test
    void llmCannotFastTrackALargeClaim() {
        GuardedDecision g = guardrails.apply(
                draft(RoutingQueue.FAST_TRACK, CoverageStatus.COVERED, 0, Severity.LOW, 0.9),
                facts(CoverageStatus.COVERED, 0, Severity.LOW), new BigDecimal("20000"));
        assertEquals(RoutingQueue.STANDARD_ADJUSTER, g.decision().queue());
    }

    @Test
    void coverageProblemsAlwaysGoToDenialReview() {
        GuardedDecision g = guardrails.apply(
                draft(RoutingQueue.STANDARD_ADJUSTER, CoverageStatus.COVERED, 0, Severity.MEDIUM, 0.9),
                facts(CoverageStatus.POLICY_INACTIVE, 0, Severity.MEDIUM), new BigDecimal("6000"));
        assertEquals(RoutingQueue.DENIAL_REVIEW, g.decision().queue());
        assertEquals(CoverageStatus.POLICY_INACTIVE, g.decision().coverageStatus());
    }

    @Test
    void llmMayEscalateBeyondTheRules() {
        GuardedDecision g = guardrails.apply(
                draft(RoutingQueue.SPECIAL_INVESTIGATIONS, CoverageStatus.COVERED, 75, Severity.MEDIUM, 0.8),
                facts(CoverageStatus.COVERED, 20, Severity.MEDIUM), new BigDecimal("6000"));
        assertEquals(RoutingQueue.SPECIAL_INVESTIGATIONS, g.decision().queue());
        assertEquals(75, g.decision().fraudRiskScore());
        assertTrue(g.notes().isEmpty());
    }

    @Test
    void severityCannotBeDowngraded() {
        GuardedDecision g = guardrails.apply(
                draft(RoutingQueue.STANDARD_ADJUSTER, CoverageStatus.COVERED, 0, Severity.MEDIUM, 0.9),
                facts(CoverageStatus.COVERED, 0, Severity.HIGH), new BigDecimal("3000"));
        assertEquals(Severity.HIGH, g.decision().severity());
        assertEquals(RoutingQueue.SENIOR_ADJUSTER, g.decision().queue());
    }

    @Test
    void lowConfidenceForcesHumanReviewEvenOnFastTrack() {
        GuardedDecision g = guardrails.apply(
                draft(RoutingQueue.FAST_TRACK, CoverageStatus.COVERED, 0, Severity.LOW, 0.3),
                facts(CoverageStatus.COVERED, 0, Severity.LOW), new BigDecimal("800"));
        assertEquals(RoutingQueue.FAST_TRACK, g.decision().queue());
        assertTrue(g.humanReviewRequired());
    }

    @Test
    void denialReviewOnACoveredClaimIsReplacedByTheFloor() {
        GuardedDecision g = guardrails.apply(
                draft(RoutingQueue.DENIAL_REVIEW, CoverageStatus.NOT_COVERED, 0, Severity.MEDIUM, 0.9),
                facts(CoverageStatus.COVERED, 0, Severity.MEDIUM), new BigDecimal("7000"));
        assertEquals(RoutingQueue.STANDARD_ADJUSTER, g.decision().queue());
        assertEquals(CoverageStatus.COVERED, g.decision().coverageStatus());
    }
}
