package com.pgds.integration;

import org.junit.jupiter.api.Test;
import org.springframework.test.annotation.DirtiesContext;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
