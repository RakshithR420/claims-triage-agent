package com.rakshith.claimstriage.service;

import com.rakshith.claimstriage.domain.*;
import com.rakshith.claimstriage.repository.AgentStepRepository;
import com.rakshith.claimstriage.repository.ClaimRepository;
import com.rakshith.claimstriage.repository.PolicyRepository;
import com.rakshith.claimstriage.repository.TriageDecisionRepository;
import com.rakshith.claimstriage.web.dto.Dtos.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Service
public class ClaimService {

    private final ClaimRepository claims;
    private final PolicyRepository policies;
    private final TriageDecisionRepository decisions;
    private final AgentStepRepository steps;
    private final TriageService triage;

    public ClaimService(ClaimRepository claims, PolicyRepository policies, TriageDecisionRepository decisions,
                        AgentStepRepository steps, TriageService triage) {
        this.claims = claims;
        this.policies = policies;
        this.decisions = decisions;
        this.steps = steps;
        this.triage = triage;
    }

    /** First notice of loss: save the claim, then triage it straight away. */
    public ClaimView submit(CreateClaimRequest req) {
        if (!policies.existsById(req.policyNumber())) {
            throw new NotFoundException("Policy " + req.policyNumber() + " not found");
        }
        LocalDate reported = req.reportedDate() != null ? req.reportedDate() : LocalDate.now();
        if (req.lossDate().isAfter(reported)) {
            throw new IllegalArgumentException("lossDate cannot be after reportedDate");
        }
        Claim claim = claims.save(new Claim(req.policyNumber(), req.claimantName(), req.lossType(),
                req.lossDate(), reported, req.claimedAmount(), req.description()));
        triage.triage(claim.getId());
        return get(claim.getId());
    }

    public ClaimView retriage(Long id) {
        triage.triage(id);
        return get(id);
    }

    @Transactional(readOnly = true)
    public ClaimView get(Long id) {
        Claim claim = claims.findById(id).orElseThrow(() -> new NotFoundException("Claim " + id + " not found"));
        return ClaimView.from(claim, decisions.findTopByClaimIdOrderByIdDesc(id).orElse(null));
    }

    @Transactional(readOnly = true)
    public List<ClaimView> list(ClaimStatus status, RoutingQueue queue) {
        List<Claim> source = status == null ? claims.findAll() : claims.findByStatus(status);
        List<ClaimView> views = new ArrayList<>();
        for (Claim c : source) {
            TriageDecision latest = decisions.findTopByClaimIdOrderByIdDesc(c.getId()).orElse(null);
            if (queue != null && (latest == null || latest.getQueue() != queue)) {
                continue;
            }
            views.add(ClaimView.from(c, latest));
        }
        views.sort(Comparator.comparing(ClaimView::id).reversed());
        return views;
    }

    @Transactional(readOnly = true)
    public List<DecisionView> decisions(Long id) {
        requireClaim(id);
        return decisions.findByClaimIdOrderByIdAsc(id).stream().map(DecisionView::from).toList();
    }

    @Transactional(readOnly = true)
    public List<StepView> trace(Long id) {
        requireClaim(id);
        return steps.findByClaimIdOrderByIdAsc(id).stream().map(StepView::from).toList();
    }

    /** An adjuster confirms or overrides the agent's routing. Recorded as a new HUMAN decision. */
    @Transactional
    public ClaimView review(Long id, ReviewRequest req) {
        Claim claim = requireClaim(id);
        TriageDecision agent = decisions.findTopByClaimIdOrderByIdDesc(id)
                .orElseThrow(() -> new IllegalStateException("Claim " + id + " has not been triaged yet"));
        String note = agent.getQueue() == req.queue()
                ? "Adjuster confirmed agent routing"
                : "Adjuster changed routing from " + agent.getQueue() + " to " + req.queue();
        decisions.save(new TriageDecision(id, agent.getSeverity(), agent.getCoverageStatus(),
                agent.getFraudRiskScore(), req.queue(), 1.0,
                req.notes() == null || req.notes().isBlank() ? note : req.notes(),
                note, "", DecisionSource.HUMAN, null, 0, false, req.reviewer()));
        claim.setStatus(ClaimStatus.REVIEWED);
        return get(id);
    }

    /** Agreement between adjusters and the agent: for each reviewed claim, did the human keep the agent's queue? */
    @Transactional(readOnly = true)
    public ReviewAgreement agreement() {
        int reviewed = 0;
        int agreed = 0;
        for (Claim c : claims.findByStatus(ClaimStatus.REVIEWED)) {
            List<TriageDecision> history = decisions.findByClaimIdOrderByIdAsc(c.getId());
            TriageDecision human = null;
            TriageDecision agentBefore = null;
            for (TriageDecision d : history) {
                if (d.getSource() == DecisionSource.HUMAN) {
                    if (human == null) human = d;   // first human review
                } else if (human == null) {
                    agentBefore = d;                // last agent decision before it
                }
            }
            if (human == null || agentBefore == null) continue;
            reviewed++;
            if (Objects.equals(human.getQueue(), agentBefore.getQueue())) agreed++;
        }
        return new ReviewAgreement(reviewed, agreed, reviewed == 0 ? 0.0 : (double) agreed / reviewed);
    }

    private Claim requireClaim(Long id) {
        return claims.findById(id).orElseThrow(() -> new NotFoundException("Claim " + id + " not found"));
    }
}
