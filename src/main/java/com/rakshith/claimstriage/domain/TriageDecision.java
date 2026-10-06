package com.rakshith.claimstriage.domain;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * One routing decision for a claim. A claim can have several over time:
 * the agent's decision, re-runs, and an adjuster's review. The latest one wins.
 */
@Entity
@Table(name = "triage_decisions", indexes = @Index(name = "idx_decision_claim", columnList = "claimId"))
public class TriageDecision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long claimId;

    @Enumerated(EnumType.STRING)
    private Severity severity;

    @Enumerated(EnumType.STRING)
    private CoverageStatus coverageStatus;

    private int fraudRiskScore;

    @Enumerated(EnumType.STRING)
    private RoutingQueue queue;

    private double confidence;

    @Column(length = 4000)
    private String summary;

    /** Newline-separated reasons the decision-maker gave. */
    @Column(length = 4000)
    private String reasons;

    /** Newline-separated guardrail corrections applied on top of the agent's draft. */
    @Column(length = 4000)
    private String guardrailNotes;

    @Enumerated(EnumType.STRING)
    private DecisionSource source;

    private String model;
    private long latencyMs;
    private boolean humanReviewRequired;
    private String reviewer;
    private Instant createdAt = Instant.now();

    protected TriageDecision() {
    }

    public TriageDecision(Long claimId, Severity severity, CoverageStatus coverageStatus, int fraudRiskScore,
                          RoutingQueue queue, double confidence, String summary, String reasons,
                          String guardrailNotes, DecisionSource source, String model, long latencyMs,
                          boolean humanReviewRequired, String reviewer) {
        this.claimId = claimId;
        this.severity = severity;
        this.coverageStatus = coverageStatus;
        this.fraudRiskScore = fraudRiskScore;
        this.queue = queue;
        this.confidence = confidence;
        this.summary = summary;
        this.reasons = reasons;
        this.guardrailNotes = guardrailNotes;
        this.source = source;
        this.model = model;
        this.latencyMs = latencyMs;
        this.humanReviewRequired = humanReviewRequired;
        this.reviewer = reviewer;
    }

    public Long getId() { return id; }
    public Long getClaimId() { return claimId; }
    public Severity getSeverity() { return severity; }
    public CoverageStatus getCoverageStatus() { return coverageStatus; }
    public int getFraudRiskScore() { return fraudRiskScore; }
    public RoutingQueue getQueue() { return queue; }
    public double getConfidence() { return confidence; }
    public String getSummary() { return summary; }
    public String getReasons() { return reasons; }
    public String getGuardrailNotes() { return guardrailNotes; }
    public DecisionSource getSource() { return source; }
    public String getModel() { return model; }
    public long getLatencyMs() { return latencyMs; }
    public boolean isHumanReviewRequired() { return humanReviewRequired; }
    public String getReviewer() { return reviewer; }
    public Instant getCreatedAt() { return createdAt; }
}
