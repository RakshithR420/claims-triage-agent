package com.rakshith.claimstriage.domain;

import jakarta.persistence.*;

import java.time.Instant;

/** Audit trail: one row per tool call the agent made while triaging a claim. */
@Entity
@Table(name = "agent_steps", indexes = @Index(name = "idx_step_claim", columnList = "claimId"))
public class AgentStep {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long claimId;
    private String runId;
    private int stepNo;
    private String toolName;

    @Column(length = 1000)
    private String input;

    @Column(length = 4000)
    private String output;

    private Instant createdAt;

    protected AgentStep() {
    }

    public AgentStep(Long claimId, String runId, int stepNo, String toolName, String input, String output, Instant createdAt) {
        this.claimId = claimId;
        this.runId = runId;
        this.stepNo = stepNo;
        this.toolName = toolName;
        this.input = input;
        this.output = output;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public Long getClaimId() { return claimId; }
    public String getRunId() { return runId; }
    public int getStepNo() { return stepNo; }
    public String getToolName() { return toolName; }
    public String getInput() { return input; }
    public String getOutput() { return output; }
    public Instant getCreatedAt() { return createdAt; }
}
