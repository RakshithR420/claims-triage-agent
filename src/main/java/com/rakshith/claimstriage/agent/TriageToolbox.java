package com.rakshith.claimstriage.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rakshith.claimstriage.agent.tools.ClaimAnalyzer;
import com.rakshith.claimstriage.agent.tools.ToolResults.*;
import com.rakshith.claimstriage.domain.Claim;
import com.rakshith.claimstriage.domain.Policy;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.List;
import java.util.function.Function;

/**
 * The tools the agent can call while triaging ONE claim.
 * <p>
 * A new toolbox is created per triage run and bound to that claim, so the model can only
 * ever read data for the claim it was given (no "look up any claim id" tool to abuse).
 * Every call is written to the {@link AgentTrace} for the audit trail.
 */
public class TriageToolbox {

    public static final String LOOKUP_POLICY = "lookupPolicy";
    public static final String CHECK_COVERAGE = "checkCoverage";
    public static final String CLAIM_HISTORY = "getClaimHistory";
    public static final String FRAUD_SIGNALS = "assessFraudSignals";
    public static final String ESTIMATE_SEVERITY = "estimateSeverity";

    private final Claim claim;
    private final Function<String, Policy> policyLookup;
    private final List<Claim> otherClaimsOnPolicy;
    private final ClaimAnalyzer analyzer;
    private final AgentTrace trace;
    private final ObjectMapper json;

    private ClaimFacts cachedFacts;

    public TriageToolbox(Claim claim, Function<String, Policy> policyLookup, List<Claim> otherClaimsOnPolicy,
                         ClaimAnalyzer analyzer, AgentTrace trace, ObjectMapper json) {
        this.claim = claim;
        this.policyLookup = policyLookup;
        this.otherClaimsOnPolicy = otherClaimsOnPolicy;
        this.analyzer = analyzer;
        this.trace = trace;
        this.json = json;
    }

    // ---------------------------------------------------------------- tools exposed to the LLM

    @Tool(name = LOOKUP_POLICY, description = "Look up an insurance policy by policy number. Returns holder, "
            + "product type, status, covered perils, coverage limit, deductible and policy period.")
    public PolicyLookup lookupPolicy(
            @ToolParam(description = "Policy number, e.g. POL-1001") String policyNumber) {
        PolicyLookup result;
        if (!claim.getPolicyNumber().equalsIgnoreCase(policyNumber == null ? "" : policyNumber.trim())) {
            result = new PolicyLookup(false, null,
                    "Access denied: you may only look up the policy on the claim being triaged ("
                            + claim.getPolicyNumber() + ")");
        } else {
            Policy policy = policyLookup.apply(claim.getPolicyNumber());
            result = policy == null
                    ? new PolicyLookup(false, null, "No policy found with number " + policyNumber)
                    : new PolicyLookup(true, PolicySummary.from(policy), "OK");
        }
        return log(LOOKUP_POLICY, policyNumber, result);
    }

    @Tool(name = CHECK_COVERAGE, description = "Check whether the claim's loss type is covered by the policy and "
            + "the policy was in force on the loss date. Returns coverage status, reasons and estimated payable.")
    public CoverageCheck checkCoverage() {
        return log(CHECK_COVERAGE, "", facts().coverage());
    }

    @Tool(name = CLAIM_HISTORY, description = "Get prior claims on the same policy: count in the last 12 months, "
            + "total count and total amount previously claimed.")
    public ClaimHistory getClaimHistory() {
        return log(CLAIM_HISTORY, "", facts().history());
    }

    @Tool(name = FRAUD_SIGNALS, description = "Run rule-based fraud indicators (late reporting, new-policy loss, "
            + "claim frequency, amount near limit, red-flag phrases). Returns a 0-100 score and the signals found.")
    public FraudAssessment assessFraudSignals() {
        return log(FRAUD_SIGNALS, "", facts().fraud());
    }

    @Tool(name = ESTIMATE_SEVERITY, description = "Estimate claim severity (LOW, MEDIUM, HIGH, CATASTROPHIC) "
            + "from the claimed amount and the loss description.")
    public SeverityEstimate estimateSeverity() {
        return log(ESTIMATE_SEVERITY, "", facts().severity());
    }

    // ---------------------------------------------------------------- ground truth (not a tool)

    /**
     * All deterministic facts for the claim, computed once. Used by the rules engine and by the
     * guardrails, which must not trust numbers the LLM merely says it saw.
     */
    public synchronized ClaimFacts facts() {
        if (cachedFacts == null) {
            Policy policy = policyLookup.apply(claim.getPolicyNumber());
            ClaimHistory history = analyzer.history(claim, otherClaimsOnPolicy);
            cachedFacts = new ClaimFacts(
                    policy == null ? null : PolicySummary.from(policy),
                    analyzer.checkCoverage(claim, policy),
                    history,
                    analyzer.assessFraud(claim, policy, history),
                    analyzer.estimateSeverity(claim));
        }
        return cachedFacts;
    }

    public Claim claim() {
        return claim;
    }

    public AgentTrace trace() {
        return trace;
    }

    private <T> T log(String tool, String input, T output) {
        trace.record(tool, input, toJson(output));
        return output;
    }

    private String toJson(Object o) {
        try {
            return json.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            return String.valueOf(o);
        }
    }
}
