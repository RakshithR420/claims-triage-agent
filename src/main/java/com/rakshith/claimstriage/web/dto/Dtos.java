package com.rakshith.claimstriage.web.dto;

import com.rakshith.claimstriage.domain.*;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

/** Request and response bodies for the REST API. */
public final class Dtos {

    private Dtos() {
    }

    public record CreateClaimRequest(
            @NotBlank String policyNumber,
            @NotBlank String claimantName,
            @NotNull LossType lossType,
            @NotNull @PastOrPresent LocalDate lossDate,
            @PastOrPresent LocalDate reportedDate,
            @NotNull @Positive @Digits(integer = 12, fraction = 2) BigDecimal claimedAmount,
            @NotBlank @Size(max = 4000) String description) {
    }

    public record ReviewRequest(
            @NotNull RoutingQueue queue,
            @NotBlank String reviewer,
            @Size(max = 2000) String notes) {
    }

    public record DecisionView(Long id, RoutingQueue queue, Severity severity, CoverageStatus coverageStatus,
                               int fraudRiskScore, double confidence, String summary, List<String> reasons,
                               List<String> guardrailNotes, DecisionSource source, String model, long latencyMs,
                               boolean humanReviewRequired, String reviewer, Instant createdAt) {

        public static DecisionView from(TriageDecision d) {
            return new DecisionView(d.getId(), d.getQueue(), d.getSeverity(), d.getCoverageStatus(),
                    d.getFraudRiskScore(), d.getConfidence(), d.getSummary(), lines(d.getReasons()),
                    lines(d.getGuardrailNotes()), d.getSource(), d.getModel(), d.getLatencyMs(),
                    d.isHumanReviewRequired(), d.getReviewer(), d.getCreatedAt());
        }

        private static List<String> lines(String s) {
            return s == null || s.isBlank() ? List.of() : Arrays.asList(s.split("\n"));
        }
    }

    public record ClaimView(Long id, String policyNumber, String claimantName, LossType lossType,
                            LocalDate lossDate, LocalDate reportedDate, BigDecimal claimedAmount,
                            String description, ClaimStatus status, DecisionView latestDecision) {

        public static ClaimView from(Claim c, TriageDecision latest) {
            return new ClaimView(c.getId(), c.getPolicyNumber(), c.getClaimantName(), c.getLossType(),
                    c.getLossDate(), c.getReportedDate(), c.getClaimedAmount(), c.getDescription(),
                    c.getStatus(), latest == null ? null : DecisionView.from(latest));
        }
    }

    public record StepView(String runId, int stepNo, String toolName, String input, String output, Instant at) {

        public static StepView from(AgentStep s) {
            return new StepView(s.getRunId(), s.getStepNo(), s.getToolName(), s.getInput(), s.getOutput(),
                    s.getCreatedAt());
        }
    }

    /** How often adjusters kept the agent's routing when they reviewed a claim. */
    public record ReviewAgreement(int reviewed, int agreed, double agreementRate) {
    }
}
