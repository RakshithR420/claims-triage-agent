package com.rakshith.claimstriage.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "claims", indexes = @Index(name = "idx_claim_policy", columnList = "policyNumber"))
public class Claim {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String policyNumber;
    private String claimantName;

    @Enumerated(EnumType.STRING)
    private LossType lossType;

    private LocalDate lossDate;
    private LocalDate reportedDate;
    private BigDecimal claimedAmount;

    @Column(length = 4000)
    private String description;

    @Enumerated(EnumType.STRING)
    private ClaimStatus status = ClaimStatus.SUBMITTED;

    private Instant createdAt = Instant.now();

    protected Claim() {
    }

    public Claim(String policyNumber, String claimantName, LossType lossType, LocalDate lossDate,
                 LocalDate reportedDate, BigDecimal claimedAmount, String description) {
        this.policyNumber = policyNumber;
        this.claimantName = claimantName;
        this.lossType = lossType;
        this.lossDate = lossDate;
        this.reportedDate = reportedDate;
        this.claimedAmount = claimedAmount;
        this.description = description;
    }

    public Long getId() { return id; }
    public String getPolicyNumber() { return policyNumber; }
    public String getClaimantName() { return claimantName; }
    public LossType getLossType() { return lossType; }
    public LocalDate getLossDate() { return lossDate; }
    public LocalDate getReportedDate() { return reportedDate; }
    public BigDecimal getClaimedAmount() { return claimedAmount; }
    public String getDescription() { return description; }
    public ClaimStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }

    public void setStatus(ClaimStatus status) { this.status = status; }
}
