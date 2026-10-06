package com.rakshith.claimstriage.web;

import com.rakshith.claimstriage.agent.tools.ToolResults.PolicySummary;
import com.rakshith.claimstriage.repository.PolicyRepository;
import com.rakshith.claimstriage.service.NotFoundException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/policies")
public class PolicyController {

    private final PolicyRepository policies;

    public PolicyController(PolicyRepository policies) {
        this.policies = policies;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<PolicySummary> list() {
        return policies.findAll().stream().map(PolicySummary::from).toList();
    }

    @GetMapping("/{policyNumber}")
    @Transactional(readOnly = true)
    public PolicySummary get(@PathVariable String policyNumber) {
        return policies.findById(policyNumber)
                .map(PolicySummary::from)
                .orElseThrow(() -> new NotFoundException("Policy " + policyNumber + " not found"));
    }
}
