package com.pgds.integration;

import org.junit.jupiter.api.Test;
import org.springframework.test.annotation.DirtiesContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class StockFlowIntegrationTest extends AbstractIntegrationTest {

    @Test
    void transfersMoveStockThroughTheLedger() throws Exception {
        String wm = login("warehouse"), dealer = login("dealer");

        // seed: warehouse opened with 10000 kg rice and sent 1000 to the shop
        assertEquals(9000.0, balance(getJson("/api/warehouse/1/stock", wm, 200), "RICE"), 0.001);
        assertEquals(1000.0, balance(getJson("/api/fps/1/stock", dealer, 200), "RICE"), 0.001);

        postJson("/api/warehouse/1/transfers", wm, "{\"fpsId\":1,\"grainType\":\"RICE\",\"quantityKg\":200}", 201);
        assertEquals(8800.0, balance(getJson("/api/warehouse/1/stock", wm, 200), "RICE"), 0.001);
        assertEquals(1200.0, balance(getJson("/api/fps/1/stock", dealer, 200), "RICE"), 0.001);

        // cannot send more than the warehouse holds
        postJson("/api/warehouse/1/transfers", wm, "{\"fpsId\":1,\"grainType\":\"RICE\",\"quantityKg\":1000000}", 409);
        // damage reduces stock, receipt adds to it
        postJson("/api/warehouse/1/damage", wm, "{\"grainType\":\"RICE\",\"quantityKg\":100,\"remarks\":\"rain\"}", 201);
        postJson("/api/warehouse/1/receipts", wm, "{\"grainType\":\"RICE\",\"quantityKg\":300}", 201);
        assertEquals(9000.0, balance(getJson("/api/warehouse/1/stock", wm, 200), "RICE"), 0.001);

        // validation: zero quantity is rejected
        postJson("/api/warehouse/1/receipts", wm, "{\"grainType\":\"RICE\",\"quantityKg\":0}", 400);
    }

    @Test
    void managersAreLimitedToTheirOwnWarehouse() throws Exception {
        String admin = login("admin"), wm = login("warehouse");
        var second = postJson("/api/admin/warehouses", admin, "{\"name\":\"Satara Godown\",\"district\":\"Satara\"}", 201);
        long id = second.get("id").asLong();
        getJson("/api/warehouse/" + id + "/stock", wm, 403);
        getJson("/api/warehouse/" + id + "/stock", admin, 200);
    }

    @Test
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void restockSuggestionsSubtractCurrentFpsStockAndAreAvailableToAdmin() throws Exception {
        String admin = login("admin");
        String wm = login("warehouse");

        var wagholi = postJson("/api/admin/fps", admin,
                "{\"shopCode\":\"FPS-TEST-WAG\",\"name\":\"Wagholi FPS\",\"district\":\"Pune\","
                        + "\"address\":\"Wagholi village\",\"warehouseId\":1}", 201);
        var pen = postJson("/api/admin/fps", admin,
                "{\"shopCode\":\"FPS-TEST-PEN\",\"name\":\"Pen FPS\",\"district\":\"Pune\","
                        + "\"address\":\"Pen village\",\"warehouseId\":1}", 201);
        postJson("/api/officer/ration-cards", login("officer"),
                "{\"cardNumber\":\"MH-PUN-WAG\",\"headOfFamily\":\"Wagholi Family\","
                        + "\"district\":\"Pune\",\"category\":\"PHH\",\"familyMembers\":10,"
                        + "\"fpsId\":" + wagholi.get("id").asLong() + "}", 201);
        postJson("/api/officer/ration-cards", login("officer"),
                "{\"cardNumber\":\"MH-PUN-PEN\",\"headOfFamily\":\"Pen Family\","
                        + "\"district\":\"Pune\",\"category\":\"PHH\",\"familyMembers\":4,"
                        + "\"fpsId\":" + pen.get("id").asLong() + "}", 201);

        postJson("/api/warehouse/1/transfers", wm,
                "{\"fpsId\":" + wagholi.get("id").asLong() + ",\"grainType\":\"RICE\",\"quantityKg\":10}", 201);

        var adminSuggestions = getJson("/api/warehouse/1/shops", admin, 200);
        var managerSuggestions = getJson("/api/warehouse/1/shops", wm, 200);
        assertEquals(adminSuggestions.size(), managerSuggestions.size());

        var seededWagholi = findShop(adminSuggestions, "FPS-WAGHOLI");
        var seededPen = findShop(adminSuggestions, "FPS-PEN");
        assertEquals(10.0, seededWagholi.get("monthlyRiceRequirementKg").asDouble(), 0.001);
        assertEquals(10.0, seededPen.get("monthlyWheatRequirementKg").asDouble(), 0.001);

        var wagholiSuggestion = findShop(adminSuggestions, "FPS-TEST-WAG");
        assertEquals(25.0, wagholiSuggestion.get("monthlyRiceRequirementKg").asDouble(), 0.001);
        assertEquals(10.0, wagholiSuggestion.get("riceStockKg").asDouble(), 0.001);
        assertEquals(10.0, wagholiSuggestion.get("riceSentThisMonthKg").asDouble(), 0.001);
        assertEquals(10.0, wagholiSuggestion.get("riceSentTotalKg").asDouble(), 0.001);
        assertEquals(15.0, wagholiSuggestion.get("suggestedRiceKg").asDouble(), 0.001);
        assertEquals(25.0, wagholiSuggestion.get("suggestedWheatKg").asDouble(), 0.001);

        var penSuggestion = findShop(adminSuggestions, "FPS-TEST-PEN");
        assertEquals(10.0, penSuggestion.get("suggestedRiceKg").asDouble(), 0.001);
        assertEquals(10.0, penSuggestion.get("suggestedWheatKg").asDouble(), 0.001);
        assertTrue(wagholiSuggestion.get("address").asText().contains("Wagholi"));
    }

    private com.fasterxml.jackson.databind.JsonNode findShop(com.fasterxml.jackson.databind.JsonNode shops, String code) {
        for (var shop : shops) if (code.equals(shop.get("shopCode").asText())) return shop;
        throw new AssertionError("Shop not found: " + code);
    }
}
