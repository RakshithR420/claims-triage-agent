package com.rakshith.claimstriage.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "policies")
public class Policy {

    @Id
    private String policyNumber;

    private String holderName;

    @Enumerated(EnumType.STRING)
    private ProductType productType;

    @Enumerated(EnumType.STRING)
    private PolicyStatus status;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "policy_covered_perils", joinColumns = @JoinColumn(name = "policy_number"))
    @Enumerated(EnumType.STRING)
    @Column(name = "loss_type")
    private Set<LossType> coveredPerils = new HashSet<>();

    private BigDecimal coverageLimit;
    private BigDecimal deductible;
    private LocalDate effectiveDate;
    private LocalDate expiryDate;

    protected Policy() {
    }

    public Policy(String policyNumber, String holderName, ProductType productType, PolicyStatus status,
                  Set<LossType> coveredPerils, BigDecimal coverageLimit, BigDecimal deductible,
                  LocalDate effectiveDate, LocalDate expiryDate) {
        this.policyNumber = policyNumber;
        this.holderName = holderName;
        this.productType = productType;
        this.status = status;
        this.coveredPerils = coveredPerils.isEmpty() ? new HashSet<>() : new HashSet<>(EnumSet.copyOf(coveredPerils));
        this.coverageLimit = coverageLimit;
        this.deductible = deductible;
        this.effectiveDate = effectiveDate;
        this.expiryDate = expiryDate;
    }

    public boolean isInForceOn(LocalDate date) {
        return status == PolicyStatus.ACTIVE
                && date != null
                && !date.isBefore(effectiveDate)
                && !date.isAfter(expiryDate);
    }

    public String getPolicyNumber() { return policyNumber; }
    public String getHolderName() { return holderName; }
    public ProductType getProductType() { return productType; }
    public PolicyStatus getStatus() { return status; }
    public Set<LossType> getCoveredPerils() { return coveredPerils; }
    public BigDecimal getCoverageLimit() { return coverageLimit; }
    public BigDecimal getDeductible() { return deductible; }
    public LocalDate getEffectiveDate() { return effectiveDate; }
    public LocalDate getExpiryDate() { return expiryDate; }
}
