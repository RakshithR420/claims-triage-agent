package com.rakshith.claimstriage;

import com.rakshith.claimstriage.eval.EvalService;
import com.rakshith.claimstriage.eval.EvalService.CaseResult;
import com.rakshith.claimstriage.eval.EvalService.EvalReport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

/** With the LLM switched off, the rules engine must route every labelled eval claim correctly. */
@SpringBootTest(properties = {"triage.llm.enabled=false", "spring.datasource.url=jdbc:h2:mem:${random.uuid};DB_CLOSE_DELAY=-1"})
class RulesEngineEvalTest {

    @Autowired
    EvalService eval;

    @Test
    void rulesEngineMatchesEveryLabel() {
        EvalReport report = eval.run();
        StringBuilder misses = new StringBuilder();
        for (CaseResult r : report.cases()) {
            if (!r.correct()) {
                misses.append(r.id()).append(": expected ").append(r.expected())
                        .append(" got ").append(r.actual()).append(" (fraud ").append(r.fraudScore())
                        .append(", ").append(r.severity()).append(")\n");
            }
        }
        assertEquals(18, report.total());
        assertEquals(report.total(), report.correct(), "Mis-routed cases:\n" + misses);
        assertEquals(0, report.underRouted());
    }
}
