package com.rakshith.claimstriage.agent;

import com.rakshith.claimstriage.agent.tools.ToolResults.ClaimFacts;
import com.rakshith.claimstriage.domain.CoverageStatus;
import com.rakshith.claimstriage.domain.RoutingQueue;
import com.rakshith.claimstriage.domain.Severity;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Checks a proposed decision against the deterministic facts and corrects it where it breaks
 * a hard business rule. The LLM can be <i>more</i> cautious than the rules, never less:
 * <ol>
 *   <li>Coverage status always comes from the coverage check, not the model.</li>
 *   <li>Fraud score and severity can be raised by the model but not lowered below the tools' values.</li>
 *   <li>The queue can't be below {@link RoutingRules#minimumQueue} (e.g. no fast-tracking a $20k claim,
 *       no skipping Special Investigations at fraud score 70+).</li>
 *   <li>Coverage problems always go to DENIAL_REVIEW - the agent never denies a claim on its own.</li>
 *   <li>Anything not fast-tracked, or with low confidence, needs a human to confirm it.</li>
 * </ol>
 */
@Component
public class GuardrailService {

    public record GuardedDecision(TriageDraft decision, List<String> notes, boolean humanReviewRequired) {
    }

    private final RoutingRules rules;

    public GuardrailService(RoutingRules rules) {
        this.rules = rules;
    }

    public GuardedDecision apply(TriageDraft draft, ClaimFacts facts, BigDecimal claimedAmount) {
        List<String> notes = new ArrayList<>();

        // 1. coverage: ground truth wins
        CoverageStatus coverage = facts.coverage().status();
        if (draft.coverageStatus() != coverage) {
            notes.add("Coverage corrected from " + draft.coverageStatus() + " to " + coverage
                    + " (coverage check result)");
        }

        // 2. fraud and severity: model may raise, not lower
        int toolFraud = facts.fraud().score();
        int fraud = clamp(draft.fraudRiskScore(), 0, 100);
        if (fraud < toolFraud) {
            notes.add("Fraud score raised from " + fraud + " to " + toolFraud + " (rule-based signals)");
            fraud = toolFraud;
        }
        Severity toolSeverity = facts.severity().severity();
        Severity severity = Severity.max(draft.severity(), toolSeverity);
        if (draft.severity() != severity) {
            notes.add("Severity raised from " + draft.severity() + " to " + severity + " (severity estimate)");
        }

        // 3 + 4. queue floor
        RoutingQueue floor = rules.minimumQueue(coverage, fraud, severity, claimedAmount);
        RoutingQueue proposed = draft.queue();
        RoutingQueue queue;
        if (floor == RoutingQueue.DENIAL_REVIEW) {
            queue = RoutingQueue.DENIAL_REVIEW;
        } else if (proposed == null || proposed == RoutingQueue.DENIAL_REVIEW) {
            // claim is covered, so denial review is not a valid destination
            queue = floor;
        } else {
            queue = RoutingQueue.atLeast(proposed, floor);
        }
        if (queue != proposed) {
            notes.add("Queue changed from " + proposed + " to " + queue + " (minimum allowed: " + floor + ")");
        }

        // 5. human in the loop
        double confidence = Double.isNaN(draft.confidence()) ? 0.0 : Math.max(0.0, Math.min(1.0, draft.confidence()));
        boolean lowConfidence = confidence < rules.minConfidence();
        if (lowConfidence) {
            notes.add("Confidence " + confidence + " is below " + rules.minConfidence() + "; human review required");
        }
        boolean humanReview = queue != RoutingQueue.FAST_TRACK || lowConfidence;

        List<String> reasons = draft.reasons() == null ? List.of() : List.copyOf(draft.reasons());
        TriageDraft corrected = new TriageDraft(severity, coverage, fraud, queue, confidence, draft.summary(), reasons);
        return new GuardedDecision(corrected, List.copyOf(notes), humanReview);
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}
