package com.rakshith.claimstriage.web;

import com.rakshith.claimstriage.eval.EvalService;
import com.rakshith.claimstriage.eval.EvalService.EvalReport;
import com.rakshith.claimstriage.service.ClaimService;
import com.rakshith.claimstriage.web.dto.Dtos.ReviewAgreement;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/eval")
public class EvalController {

    private final EvalService eval;
    private final ClaimService claims;

    public EvalController(EvalService eval, ClaimService claims) {
        this.eval = eval;
        this.claims = claims;
    }

    /** Runs the labelled eval set through the agent + guardrails (nothing is saved). */
    @PostMapping("/run")
    public EvalReport run() {
        return eval.run();
    }

    /** Live metric: how often adjusters kept the agent's routing. */
    @GetMapping("/review-agreement")
    public ReviewAgreement reviewAgreement() {
        return claims.agreement();
    }
}
