package com.rakshith.claimstriage.repository;

import com.rakshith.claimstriage.domain.DecisionSource;
import com.rakshith.claimstriage.domain.TriageDecision;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TriageDecisionRepository extends JpaRepository<TriageDecision, Long> {

    Optional<TriageDecision> findTopByClaimIdOrderByIdDesc(Long claimId);

    List<TriageDecision> findByClaimIdOrderByIdAsc(Long claimId);

    List<TriageDecision> findBySource(DecisionSource source);
}
