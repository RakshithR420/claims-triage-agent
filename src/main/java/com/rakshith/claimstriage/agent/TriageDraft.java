package com.rakshith.claimstriage.agent;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.rakshith.claimstriage.domain.CoverageStatus;
import com.rakshith.claimstriage.domain.RoutingQueue;
import com.rakshith.claimstriage.domain.Severity;

import java.util.List;

/**
 * The structured decision a decision engine proposes. For the LLM this record is also the
 * output schema: Spring AI turns it (and the descriptions below) into JSON-schema format
 * instructions and parses the model's reply back into it.
 */
public record TriageDraft(
        @JsonPropertyDescription("Severity of the loss: LOW, MEDIUM, HIGH or CATASTROPHIC")
        Severity severity,

        @JsonPropertyDescription("Coverage status: COVERED, NOT_COVERED, POLICY_INACTIVE or NEEDS_REVIEW")
        CoverageStatus coverageStatus,

        @JsonPropertyDescription("Fraud risk from 0 (none) to 100 (very likely fraud)")
        int fraudRiskScore,

        @JsonPropertyDescription("Queue to route to: FAST_TRACK, STANDARD_ADJUSTER, SENIOR_ADJUSTER, "
                + "SPECIAL_INVESTIGATIONS or DENIAL_REVIEW")
        RoutingQueue queue,

        @JsonPropertyDescription("Your confidence in this routing, from 0.0 to 1.0")
        double confidence,

        @JsonPropertyDescription("Two to four sentence summary of the claim for the adjuster who picks it up")
        String summary,

        @JsonPropertyDescription("Short bullet-style reasons for the routing decision")
        List<String> reasons
) {
}
