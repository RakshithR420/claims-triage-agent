package com.rakshith.claimstriage.agent.tools;

import com.rakshith.claimstriage.domain.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * Plain records returned by the agent's tools. Spring AI serialises them to JSON for the model,
 * and the guardrails use the same records as "ground truth".
 */
public final class ToolResults {

    private ToolResults() {
    }

    public record PolicySummary(String policyNumber, String holderName, ProductType productType,
                                PolicyStatus status, Set<LossType> coveredPerils, BigDecimal coverageLimit,
                                BigDecimal deductible, LocalDate effectiveDate, LocalDate expiryDate) {

        public static PolicySummary from(Policy p) {
            return new PolicySummary(p.getPolicyNumber(), p.getHolderName(), p.getProductType(), p.getStatus(),
                    Set.copyOf(p.getCoveredPerils()), p.getCoverageLimit(), p.getDeductible(),
                    p.getEffectiveDate(), p.getExpiryDate());
        }
    }

    public record PolicyLookup(boolean found, PolicySummary policy, String message) {
    }

    public record CoverageCheck(CoverageStatus status, List<String> reasons, BigDecimal coverageLimit,
                                BigDecimal deductible, BigDecimal estimatedPayable) {
    }

    public record ClaimHistory(int priorClaimsLast12Months, int priorClaimsTotal,
                               BigDecimal priorClaimedAmountTotal) {
    }

    /** @param score 0 (no signals) to 100 (very likely fraudulent) */
    public record FraudAssessment(int score, List<String> signals) {
    }

    public record SeverityEstimate(Severity severity, List<String> reasons) {
    }

    /** Everything the deterministic tools know about a claim. Used by the rules engine and the guardrails. */
    public record ClaimFacts(PolicySummary policy, CoverageCheck coverage, ClaimHistory history,
                             FraudAssessment fraud, SeverityEstimate severity) {
    }
}
