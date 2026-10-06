package com.rakshith.claimstriage;

import com.rakshith.claimstriage.agent.tools.ClaimAnalyzer;
import com.rakshith.claimstriage.agent.tools.ToolResults.*;
import com.rakshith.claimstriage.domain.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ClaimAnalyzerTest {

    private final ClaimAnalyzer analyzer = new ClaimAnalyzer();
    private final LocalDate today = LocalDate.now();

    private Policy autoPolicy(PolicyStatus status, LocalDate effective) {
        return new Policy("POL-T1", "Test Holder", ProductType.AUTO, status,
                Set.of(LossType.COLLISION, LossType.THEFT), new BigDecimal("20000"), new BigDecimal("500"),
                effective, today.plusYears(1));
    }

    private Claim claim(LossType type, int lossDaysAgo, int reportedDaysAgo, String amount, String text) {
        return new Claim("POL-T1", "Test", type, today.minusDays(lossDaysAgo), today.minusDays(reportedDaysAgo),
                new BigDecimal(amount), text);
    }

    @Test
    void coveredClaimGetsPayableAfterDeductible() {
        CoverageCheck c = analyzer.checkCoverage(claim(LossType.COLLISION, 3, 2, "3000", "dent"),
                autoPolicy(PolicyStatus.ACTIVE, today.minusYears(1)));
        assertEquals(CoverageStatus.COVERED, c.status());
        assertEquals(0, new BigDecimal("2500").compareTo(c.estimatedPayable()));
    }

    @Test
    void uncoveredPerilIsNotCovered() {
        CoverageCheck c = analyzer.checkCoverage(claim(LossType.FIRE, 3, 2, "3000", "engine fire"),
                autoPolicy(PolicyStatus.ACTIVE, today.minusYears(1)));
        assertEquals(CoverageStatus.NOT_COVERED, c.status());
    }

    @Test
    void lapsedPolicyIsInactive() {
        CoverageCheck c = analyzer.checkCoverage(claim(LossType.COLLISION, 3, 2, "3000", "dent"),
                autoPolicy(PolicyStatus.LAPSED, today.minusYears(1)));
        assertEquals(CoverageStatus.POLICY_INACTIVE, c.status());
    }

    @Test
    void missingPolicyNeedsReview() {
        CoverageCheck c = analyzer.checkCoverage(claim(LossType.COLLISION, 3, 2, "3000", "dent"), null);
        assertEquals(CoverageStatus.NEEDS_REVIEW, c.status());
    }

    @Test
    void cleanClaimHasNoFraudSignals() {
        Claim c = claim(LossType.COLLISION, 3, 2, "1200", "Minor scratch in parking lot");
        FraudAssessment f = analyzer.assessFraud(c, autoPolicy(PolicyStatus.ACTIVE, today.minusYears(1)),
                new ClaimHistory(0, 0, BigDecimal.ZERO));
        assertEquals(0, f.score());
        assertTrue(f.signals().isEmpty());
    }

    @Test
    void stackedRedFlagsAddUp() {
        // new policy (+30), late reporting (+20), two red-flag phrases (+30), frequency (+25) -> capped at 100
        Claim c = claim(LossType.THEFT, 40, 1, "4000", "No witnesses and I lost the receipt");
        FraudAssessment f = analyzer.assessFraud(c, autoPolicy(PolicyStatus.ACTIVE, today.minusDays(45)),
                new ClaimHistory(2, 2, new BigDecimal("5000")));
        assertEquals(100, f.score());
        assertEquals(5, f.signals().size());
    }

    @Test
    void severityUsesAmountAndText() {
        assertEquals(Severity.LOW, analyzer.estimateSeverity(claim(LossType.COLLISION, 1, 1, "900", "scratch")).severity());
        assertEquals(Severity.MEDIUM, analyzer.estimateSeverity(claim(LossType.COLLISION, 1, 1, "9000", "bumper")).severity());
        assertEquals(Severity.HIGH, analyzer.estimateSeverity(
                claim(LossType.COLLISION, 1, 1, "900", "Rider injured, taken to hospital")).severity());
        assertEquals(Severity.CATASTROPHIC, analyzer.estimateSeverity(claim(LossType.COLLISION, 1, 1, "150000", "x")).severity());
    }

    @Test
    void historyExcludesTheClaimItselfAndOldClaims() {
        Claim current = claim(LossType.COLLISION, 2, 1, "1000", "x");
        Claim recent = claim(LossType.THEFT, 60, 60, "500", "y");
        Claim old = claim(LossType.THEFT, 500, 500, "700", "z");
        ClaimHistory h = analyzer.history(current, List.of(recent, old));
        assertEquals(1, h.priorClaimsLast12Months());
        assertEquals(2, h.priorClaimsTotal());
    }
}
