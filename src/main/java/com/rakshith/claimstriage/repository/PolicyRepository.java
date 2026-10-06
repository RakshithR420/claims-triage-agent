package com.rakshith.claimstriage.repository;

import com.rakshith.claimstriage.domain.Policy;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PolicyRepository extends JpaRepository<Policy, String> {
}
