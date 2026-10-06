package com.rakshith.claimstriage;

import com.rakshith.claimstriage.agent.LlmDecisionEngine;
import com.rakshith.claimstriage.agent.TriageDraft;
import com.rakshith.claimstriage.domain.*;
import com.rakshith.claimstriage.service.TriageService;
import com.rakshith.claimstriage.service.TriageService.TriageOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Simulates the LLM with a mock so we can test the two things that matter most without a model:
 * (1) a broken/slow model falls back to the rules engine, (2) a wrong model answer is corrected.
 */
@SpringBootTest(properties = {"triage.llm.enabled=false", "spring.datasource.url=jdbc:h2:mem:${random.uuid};DB_CLOSE_DELAY=-1"})
@DirtiesContext
class LlmFallbackAndGuardrailTest {

    @MockitoBean
    LlmDecisionEngine llm;

    @Autowired
    TriageService triage;

    @BeforeEach
    void setUp() {
        when(llm.source()).thenReturn(DecisionSource.LLM_AGENT);
        when(llm.modelName()).thenReturn("mock-model");
    }

    private Claim suspiciousTheft() {
        LocalDate today = LocalDate.now();
        return new Claim("POL-1006", "Test", LossType.THEFT, today.minusDays(40), today.minusDays(1),
                new BigDecimal("22000"), "Car stolen from my friend's garage, no police report filed yet.");
    }

    @Test
    void llmFailureFallsBackToRulesEngine() {
        when(llm.decide(any())).thenThrow(new RuntimeException("connection refused"));

        TriageOutcome out = triage.evaluate(suspiciousTheft());

        assertEquals(DecisionSource.RULES_ENGINE, out.source());
        assertEquals(RoutingQueue.SPECIAL_INVESTIGATIONS, out.guarded().decision().queue());
        assertTrue(out.guarded().notes().get(0).startsWith("LLM agent failed"));
    }

    @Test
    void underRoutingLlmAnswerIsCorrectedByGuardrails() {
        when(llm.decide(any())).thenReturn(new TriageDraft(Severity.LOW, CoverageStatus.COVERED, 5,
                RoutingQueue.FAST_TRACK, 0.97, "Looks routine.", List.of("Customer seems honest")));

        TriageOutcome out = triage.evaluate(suspiciousTheft());

        assertEquals(DecisionSource.LLM_AGENT, out.source());
        assertEquals(RoutingQueue.SPECIAL_INVESTIGATIONS, out.guarded().decision().queue());
        assertTrue(out.guarded().decision().fraudRiskScore() >= 70);
        assertTrue(out.guarded().humanReviewRequired());
        assertFalse(out.guarded().notes().isEmpty());
    }
}
