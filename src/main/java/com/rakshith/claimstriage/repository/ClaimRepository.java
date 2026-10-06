package com.rakshith.claimstriage.repository;

import com.rakshith.claimstriage.domain.Claim;
import com.rakshith.claimstriage.domain.ClaimStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ClaimRepository extends JpaRepository<Claim, Long> {

    List<Claim> findByPolicyNumber(String policyNumber);

    List<Claim> findByStatus(ClaimStatus status);
}
