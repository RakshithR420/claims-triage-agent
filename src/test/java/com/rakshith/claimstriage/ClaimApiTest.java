package com.rakshith.claimstriage;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** End-to-end through the REST API with the rules engine (no LLM needed). */
@SpringBootTest(properties = {"triage.llm.enabled=false", "spring.datasource.url=jdbc:h2:mem:${random.uuid};DB_CLOSE_DELAY=-1"})
@AutoConfigureMockMvc
@DirtiesContext
class ClaimApiTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    private String claimJson(String policy, String lossType, String amount, String description) {
        return """
                {"policyNumber":"%s","claimantName":"Test User","lossType":"%s","lossDate":"%s",
                 "claimedAmount":%s,"description":"%s"}
                """.formatted(policy, lossType, LocalDate.now().minusDays(2), amount, description);
    }

    private JsonNode body(org.springframework.test.web.servlet.ResultActions r) throws Exception {
        return json.readTree(r.andReturn().getResponse().getContentAsString());
    }

    @Test
    void smallCleanClaimIsFastTrackedWithAnAuditTrail() throws Exception {
        JsonNode claim = body(mvc.perform(post("/api/claims").contentType(MediaType.APPLICATION_JSON)
                        .content(claimJson("POL-1004", "TRAVEL_DELAY", "300", "Flight delayed 7 hours, meals claimed")))
                .andExpect(status().isCreated()));

        assertEquals("FAST_TRACK", claim.at("/latestDecision/queue").asText());
        assertEquals("TRIAGED", claim.get("status").asText());
        assertEquals("RULES_ENGINE", claim.at("/latestDecision/source").asText());

        long id = claim.get("id").asLong();
        JsonNode trace = body(mvc.perform(get("/api/claims/" + id + "/trace")).andExpect(status().isOk()));
        assertEquals(5, trace.size(), "one audit row per tool call");
        assertEquals("lookupPolicy", trace.get(0).get("toolName").asText());
    }

    @Test
    void lapsedPolicyGoesToDenialReviewAndAdjusterCanOverride() throws Exception {
        JsonNode claim = body(mvc.perform(post("/api/claims").contentType(MediaType.APPLICATION_JSON)
                        .content(claimJson("POL-1003", "COLLISION", "2500", "Rear bumper dent")))
                .andExpect(status().isCreated()));
        assertEquals("DENIAL_REVIEW", claim.at("/latestDecision/queue").asText());
        assertEquals("PENDING_HUMAN_REVIEW", claim.get("status").asText());

        long id = claim.get("id").asLong();
        JsonNode reviewed = body(mvc.perform(post("/api/claims/" + id + "/review").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"queue\":\"STANDARD_ADJUSTER\",\"reviewer\":\"adjuster.ravi\",\"notes\":\"Renewal payment was in grace period\"}"))
                .andExpect(status().isOk()));
        assertEquals("REVIEWED", reviewed.get("status").asText());
        assertEquals("HUMAN", reviewed.at("/latestDecision/source").asText());
        assertEquals("STANDARD_ADJUSTER", reviewed.at("/latestDecision/queue").asText());

        JsonNode history = body(mvc.perform(get("/api/claims/" + id + "/decisions")).andExpect(status().isOk()));
        assertEquals(2, history.size());

        JsonNode agreement = body(mvc.perform(get("/api/eval/review-agreement")).andExpect(status().isOk()));
        assertTrue(agreement.get("reviewed").asInt() >= 1);
    }

    @Test
    void unknownPolicyIs404() throws Exception {
        mvc.perform(post("/api/claims").contentType(MediaType.APPLICATION_JSON)
                        .content(claimJson("POL-9999", "COLLISION", "1000", "x")))
                .andExpect(status().isNotFound());
    }

    @Test
    void invalidRequestIs400() throws Exception {
        mvc.perform(post("/api/claims").contentType(MediaType.APPLICATION_JSON)
                        .content(claimJson("POL-1001", "COLLISION", "-5", "")))
                .andExpect(status().isBadRequest());
    }
}
