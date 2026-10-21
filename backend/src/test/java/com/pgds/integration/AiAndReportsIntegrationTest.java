package com.pgds.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Steps share state (demo history can only be generated once), so they run in a fixed order. */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AiAndReportsIntegrationTest extends AbstractIntegrationTest {

    @Test @Order(1)
    void demoHistoryIsGeneratedOnce() throws Exception {
        String admin = login("admin");
        JsonNode r = postJson("/api/admin/ai/demo-history?fpsId=1", admin, null, 200);
        assertEquals(60, r.get("cards").asInt());
        assertEquals(34, r.get("injectedAnomalies").asInt());
        postJson("/api/admin/ai/demo-history?fpsId=1", admin, null, 409);
        getJson("/api/admin/warehouses", login("officer"), 403);
    }

    @Test @Order(2)
    void analysisRaisesAlerts() throws Exception {
        String officer = login("officer");
        JsonNode run = postJson("/api/officer/ai/run", officer, null, 200);
        assertTrue(run.get("anomalyAlerts").asInt() > 0, "injected anomalies should raise alerts");
        JsonNode open = getJson("/api/officer/ai/alerts?status=OPEN&size=50", officer, 200);
        assertTrue(open.get("items").size() > 0);
        // running again must not duplicate alerts
        assertEquals(0, postJson("/api/officer/ai/run", officer, null, 200).get("anomalyAlerts").asInt());
        // officer can review an alert
        long id = open.get("items").get(0).get("id").asLong();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/officer/ai/alerts/" + id)
                        .header("Authorization", "Bearer " + officer).contentType("application/json")
                        .content("{\"status\":\"RESOLVED\",\"note\":\"checked\"}"))
                .andExpect(status().isOk());
        // dashboard counts AI alerts
        assertTrue(getJson("/api/officer/dashboard/summary", officer, 200).get("openAiAlerts").asInt() > 0);
    }

    @Test @Order(3)
    void detectorsFindMostInjectedAnomalies() throws Exception {
        JsonNode e = getJson("/api/officer/ai/evaluation", login("officer"), 200);
        assertEquals(34, e.get("labelledAnomalies").asInt());
        assertTrue(e.get("combined").get("recall").asDouble() >= 0.9, "rules + forest should catch nearly all injected anomalies");
        assertTrue(e.get("combined").get("precision").asDouble() > 0.5);
    }

    @Test @Order(4)
    void forecastsAndModelInfoRespond() throws Exception {
        String officer = login("officer");
        assertTrue(getJson("/api/officer/ai/stock-forecast?type=FPS", officer, 200).size() > 0);
        assertTrue(getJson("/api/officer/ai/demand", officer, 200).size() > 0);
        assertTrue(getJson("/api/officer/ai/model-info", officer, 200).get("trained").asBoolean());
        JsonNode c = postJson("/api/officer/ai/classify", officer, "{\"text\":\"rice had insects and bad smell\"}", 200);
        assertEquals("POOR_QUALITY", c.get("category").asText());
    }

    @Test @Order(5)
    void reportsReturnJsonAndCsv() throws Exception {
        String officer = login("officer"), auditor = login("auditor");
        JsonNode monthly = getJson("/api/officer/reports/monthly?months=6", officer, 200);
        assertEquals(6, monthly.size());
        boolean anyData = false;
        for (JsonNode m : monthly) anyData |= m.get("totalKg").asDouble() > 0;
        assertTrue(anyData, "demo history should appear in the monthly report");

        assertTrue(getJson("/api/officer/reports/districts", officer, 200).size() > 0);
        assertTrue(getJson("/api/audit/reports/shops", auditor, 200).size() > 0);
        getJson("/api/officer/reports/shops?period=bad", officer, 400);
        getJson("/api/officer/reports/shops", login("dealer"), 403);

        MvcResult csv = mvc.perform(get("/api/officer/reports/shops.csv").header("Authorization", "Bearer " + officer))
                .andExpect(status().isOk()).andReturn();
        assertTrue(csv.getResponse().getContentType().startsWith("text/csv"));
        assertTrue(csv.getResponse().getContentAsString().startsWith("shop_code,shop_name,district"));
        assertTrue(csv.getResponse().getHeader("Content-Disposition").contains("shop-report.csv"));
    }
}
