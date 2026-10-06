package com.rakshith.claimstriage.web;

import com.rakshith.claimstriage.domain.ClaimStatus;
import com.rakshith.claimstriage.domain.RoutingQueue;
import com.rakshith.claimstriage.service.ClaimService;
import com.rakshith.claimstriage.web.dto.Dtos.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/claims")
public class ClaimController {

    private final ClaimService claims;

    public ClaimController(ClaimService claims) {
        this.claims = claims;
    }

    /** First notice of loss: creates the claim and triages it immediately. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ClaimView submit(@Valid @RequestBody CreateClaimRequest request) {
        return claims.submit(request);
    }

    /** Work queues: filter by claim status and/or by the queue of the latest decision. */
    @GetMapping
    public List<ClaimView> list(@RequestParam(required = false) ClaimStatus status,
                                @RequestParam(required = false) RoutingQueue queue) {
        return claims.list(status, queue);
    }

    @GetMapping("/{id}")
    public ClaimView get(@PathVariable Long id) {
        return claims.get(id);
    }

    /** Every decision for the claim: agent runs and human reviews, oldest first. */
    @GetMapping("/{id}/decisions")
    public List<DecisionView> decisions(@PathVariable Long id) {
        return claims.decisions(id);
    }

    /** Audit trail: each tool the agent called, with input and output. */
    @GetMapping("/{id}/trace")
    public List<StepView> trace(@PathVariable Long id) {
        return claims.trace(id);
    }

    @PostMapping("/{id}/triage")
    public ClaimView retriage(@PathVariable Long id) {
        return claims.retriage(id);
    }

    /** Human in the loop: an adjuster confirms or changes the routing. */
    @PostMapping("/{id}/review")
    public ClaimView review(@PathVariable Long id, @Valid @RequestBody ReviewRequest request) {
        return claims.review(id, request);
    }
}
