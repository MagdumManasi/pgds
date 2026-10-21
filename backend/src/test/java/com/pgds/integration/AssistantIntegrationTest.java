package com.pgds.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.test.annotation.DirtiesContext;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Runs in LOCAL mode (the default), so no API key or network is needed. */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AssistantIntegrationTest extends AbstractIntegrationTest {

    private JsonNode ask(String token, String question, int expected) throws Exception {
        return postJson("/api/assistant/ask", token, "{\"question\":\"" + question + "\"}", expected);
    }

    @Test
    void answersPolicyQuestionsWithSources() throws Exception {
        JsonNode r = ask(login("officer"), "What is the monthly entitlement of an AAY card?", 200);
        assertEquals("LOCAL", r.get("mode").asText());
        assertTrue(r.get("sources").size() > 0);
        assertTrue(r.get("answer").asText().contains("35"));
    }

    @Test
    void beneficiaryGetsOnlyTheirOwnEntitlement() throws Exception {
        JsonNode r = ask(login("beneficiary"), "How much rice do I have left this month?", 200);
        String live = r.get("liveData").toString();
        assertTrue(live.contains("MH-PUN-000001") && live.contains("RICE"));
        assertFalse(live.contains("FPS102"), "no system-wide data for a beneficiary");
    }

    @Test
    void officerGetsSystemWideDataAndDealerGetsOwnShop() throws Exception {
        assertTrue(ask(login("officer"), "Which shops have low stock?", 200).get("liveData").size() > 0);
        JsonNode dealer = ask(login("dealer"), "How much stock does my shop have?", 200);
        assertTrue(dealer.get("liveData").toString().contains("Your shop"));
    }

    @Test
    void validationAuthenticationAndStatus() throws Exception {
        String t = login("officer");
        ask(t, "hi", 400);
        mvc.perform(post("/api/assistant/ask").contentType("application/json").content("{\"question\":\"hello there\"}"))
                .andExpect(status().isUnauthorized());
        JsonNode s = getJson("/api/assistant/status", t, 200);
        assertEquals("local", s.get("configuredMode").asText());
        assertEquals("KEYWORD", s.get("retrieval").asText());
        postJson("/api/admin/assistant/reindex", login("admin"), null, 200);
        postJson("/api/admin/assistant/reindex", t, null, 403);
    }
}
