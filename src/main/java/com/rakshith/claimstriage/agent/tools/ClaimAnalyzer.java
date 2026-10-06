package com.rakshith.claimstriage.agent.tools;

import com.rakshith.claimstriage.agent.tools.ToolResults.*;
import com.rakshith.claimstriage.domain.*;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Deterministic business checks behind the agent's tools. Pure functions (no I/O),
 * so they are easy to unit test and safe to use as ground truth for the guardrails.
 */
@Component
public class ClaimAnalyzer {

    static final BigDecimal SEVERITY_MEDIUM_AMOUNT = new BigDecimal("5000");
    static final BigDecimal SEVERITY_HIGH_AMOUNT = new BigDecimal("25000");
    static final BigDecimal SEVERITY_CATASTROPHIC_AMOUNT = new BigDecimal("100000");

    private static final List<String> CATASTROPHIC_WORDS = List.of("fatal", "death", "died", "destroyed", "collapsed");
    private static final List<String> HIGH_SEVERITY_WORDS = List.of(
            "injury", "injured", "hospital", "ambulance", "fracture", "total loss", "uninhabitable");
    private static final List<String> SUSPICIOUS_PHRASES = List.of(
            "cash only", "no witnesses", "no receipt", "lost the receipt", "receipts were lost",
            "did not inform police", "no police report", "friend's garage", "can't remember");

    public CoverageCheck checkCoverage(Claim claim, Policy policy) {
        List<String> reasons = new ArrayList<>();
        if (policy == null) {
            reasons.add("Policy " + claim.getPolicyNumber() + " not found");
            return new CoverageCheck(CoverageStatus.NEEDS_REVIEW, reasons, null, null, BigDecimal.ZERO);
        }
        if (policy.getStatus() != PolicyStatus.ACTIVE) {
            reasons.add("Policy status is " + policy.getStatus());
            return new CoverageCheck(CoverageStatus.POLICY_INACTIVE, reasons,
                    policy.getCoverageLimit(), policy.getDeductible(), BigDecimal.ZERO);
        }
        if (!policy.isInForceOn(claim.getLossDate())) {
            reasons.add("Loss date " + claim.getLossDate() + " is outside the policy period "
                    + policy.getEffectiveDate() + " to " + policy.getExpiryDate());
            return new CoverageCheck(CoverageStatus.POLICY_INACTIVE, reasons,
                    policy.getCoverageLimit(), policy.getDeductible(), BigDecimal.ZERO);
        }
        if (!policy.getCoveredPerils().contains(claim.getLossType())) {
            reasons.add(claim.getLossType() + " is not a covered peril on this " + policy.getProductType()
                    + " policy (covered: " + policy.getCoveredPerils() + ")");
            return new CoverageCheck(CoverageStatus.NOT_COVERED, reasons,
                    policy.getCoverageLimit(), policy.getDeductible(), BigDecimal.ZERO);
        }

        reasons.add(claim.getLossType() + " is covered and the policy was in force on the loss date");
        BigDecimal claimed = claim.getClaimedAmount();
        BigDecimal capped = claimed.min(policy.getCoverageLimit());
        if (claimed.compareTo(policy.getCoverageLimit()) > 0) {
            reasons.add("Claimed amount exceeds the coverage limit of " + policy.getCoverageLimit()
                    + "; payable is capped at the limit");
        }
        BigDecimal payable = capped.subtract(policy.getDeductible()).max(BigDecimal.ZERO);
        reasons.add("Estimated payable after deductible: " + payable);
        return new CoverageCheck(CoverageStatus.COVERED, reasons,
                policy.getCoverageLimit(), policy.getDeductible(), payable);
    }

    /** @param policyClaims all claims on the same policy (the current claim is excluded here) */
    public ClaimHistory history(Claim claim, List<Claim> policyClaims) {
        LocalDate windowStart = claim.getReportedDate().minusMonths(12);
        List<Claim> prior = policyClaims.stream()
                .filter(c -> claim.getId() == null || !Objects.equals(c.getId(), claim.getId()))
                .toList();
        int last12 = (int) prior.stream()
                .filter(c -> c.getLossDate() != null && !c.getLossDate().isBefore(windowStart))
                .count();
        BigDecimal total = prior.stream()
                .map(Claim::getClaimedAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new ClaimHistory(last12, prior.size(), total);
    }

    public FraudAssessment assessFraud(Claim claim, Policy policy, ClaimHistory history) {
        int score = 0;
        List<String> signals = new ArrayList<>();

        if (claim.getLossDate().isAfter(claim.getReportedDate())) {
            score += 40;
            signals.add("Loss date is after the reported date (data integrity issue)");
        }

        long reportingDelay = ChronoUnit.DAYS.between(claim.getLossDate(), claim.getReportedDate());
        if (reportingDelay > 30) {
            score += 20;
            signals.add("Reported " + reportingDelay + " days after the loss (late reporting)");
        }

        if (policy != null) {
            long daysIntoPolicy = ChronoUnit.DAYS.between(policy.getEffectiveDate(), claim.getLossDate());
            if (daysIntoPolicy >= 0 && daysIntoPolicy <= 30) {
                score += 30;
                signals.add("Loss occurred " + daysIntoPolicy + " days after the policy started (new-policy loss)");
            }
            BigDecimal ninetyPercentOfLimit = policy.getCoverageLimit().multiply(new BigDecimal("0.9"));
            if (claim.getClaimedAmount().compareTo(ninetyPercentOfLimit) > 0) {
                score += 20;
                signals.add("Claimed amount is over 90% of the coverage limit");
            }
        }

        if (history.priorClaimsLast12Months() >= 2) {
            score += 25;
            signals.add(history.priorClaimsLast12Months() + " prior claims on this policy in the last 12 months");
        }

        String text = lower(claim.getDescription());
        int phraseHits = 0;
        for (String phrase : SUSPICIOUS_PHRASES) {
            if (text.contains(phrase) && phraseHits < 2) {
                phraseHits++;
                score += 15;
                signals.add("Description contains a red-flag phrase: \"" + phrase + "\"");
            }
        }

        BigDecimal amount = claim.getClaimedAmount();
        if (amount.compareTo(new BigDecimal("10000")) >= 0
                && amount.remainder(new BigDecimal("1000")).compareTo(BigDecimal.ZERO) == 0) {
            score += 5;
            signals.add("Large round-number claim amount");
        }

        return new FraudAssessment(Math.min(score, 100), signals);
    }

    public SeverityEstimate estimateSeverity(Claim claim) {
        BigDecimal amount = claim.getClaimedAmount().setScale(2, RoundingMode.HALF_UP);
        String text = lower(claim.getDescription());
        List<String> reasons = new ArrayList<>();

        Severity byAmount;
        if (amount.compareTo(SEVERITY_CATASTROPHIC_AMOUNT) >= 0) {
            byAmount = Severity.CATASTROPHIC;
        } else if (amount.compareTo(SEVERITY_HIGH_AMOUNT) >= 0) {
            byAmount = Severity.HIGH;
        } else if (amount.compareTo(SEVERITY_MEDIUM_AMOUNT) >= 0) {
            byAmount = Severity.MEDIUM;
        } else {
            byAmount = Severity.LOW;
        }
        reasons.add("Claimed amount " + amount + " maps to " + byAmount);

        Severity byText = Severity.LOW;
        for (String w : CATASTROPHIC_WORDS) {
            if (text.contains(w)) {
                byText = Severity.CATASTROPHIC;
                reasons.add("Description mentions \"" + w + "\"");
                break;
            }
        }
        if (byText == Severity.LOW) {
            for (String w : HIGH_SEVERITY_WORDS) {
                if (text.contains(w)) {
                    byText = Severity.HIGH;
                    reasons.add("Description mentions \"" + w + "\"");
                    break;
                }
            }
        }
        return new SeverityEstimate(Severity.max(byAmount, byText), reasons);
    }

    private static String lower(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }
}
