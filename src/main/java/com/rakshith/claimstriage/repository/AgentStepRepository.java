package com.rakshith.claimstriage.repository;

import com.rakshith.claimstriage.domain.AgentStep;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AgentStepRepository extends JpaRepository<AgentStep, Long> {

    List<AgentStep> findByClaimIdOrderByIdAsc(Long claimId);
}
