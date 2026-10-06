package com.rakshith.claimstriage.agent;

import com.rakshith.claimstriage.agent.tools.ToolResults.*;
import com.rakshith.claimstriage.domain.DecisionSource;
import com.rakshith.claimstriage.domain.RoutingQueue;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic decision engine. Calls the same tools as the LLM (so the audit trail looks the same)
 * and applies {@link RoutingRules}. Used when the LLM is disabled, and as the fallback when the LLM
 * times out or returns something unusable.
 */
@Component
public class RulesDecisionEngine implements DecisionEngine {

    private final RoutingRules rules;

    public RulesDecisionEngine(RoutingRules rules) {
        this.rules = rules;
    }

    @Override
    public TriageDraft decide(TriageToolbox tools) {
        var claim = tools.claim();
        PolicyLookup policy = tools.lookupPolicy(claim.getPolicyNumber());
        CoverageCheck coverage = tools.checkCoverage();
        ClaimHistory history = tools.getClaimHistory();
        FraudAssessment fraud = tools.assessFraudSignals();
        SeverityEstimate severity = tools.estimateSeverity();

        RoutingQueue queue = rules.minimumQueue(coverage.status(), fraud.score(), severity.severity(),
                claim.getClaimedAmount());

        List<String> reasons = new ArrayList<>();
        reasons.add("Coverage: " + coverage.status() + " - " + String.join("; ", coverage.reasons()));
        reasons.add("Severity: " + severity.severity() + " - " + String.join("; ", severity.reasons()));
        reasons.add("Fraud score " + fraud.score()
                + (fraud.signals().isEmpty() ? " (no signals)" : " - " + String.join("; ", fraud.signals())));
        reasons.add("Prior claims on policy in last 12 months: " + history.priorClaimsLast12Months());

        String summary = "%s claim on %s policy %s for %s. Coverage %s, severity %s, fraud score %d. Routed to %s."
                .formatted(claim.getLossType(),
                        policy.found() ? policy.policy().productType() : "unknown",
                        claim.getPolicyNumber(), claim.getClaimedAmount(),
                        coverage.status(), severity.severity(), fraud.score(), queue);

        return new TriageDraft(severity.severity(), coverage.status(), fraud.score(), queue, 1.0, summary, reasons);
    }

    @Override
    public DecisionSource source() {
        return DecisionSource.RULES_ENGINE;
    }

    @Override
    public String modelName() {
        return "rules-v1";
    }
}
