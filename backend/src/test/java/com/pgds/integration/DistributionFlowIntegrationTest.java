package com.pgds.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.test.annotation.DirtiesContext;

import static org.junit.jupiter.api.Assertions.*;

/** The core business flow: assign card to shop -> check entitlement -> issue -> limits -> ledger -> beneficiary view -> dashboard. */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class DistributionFlowIntegrationTest extends AbstractIntegrationTest {

    @Test
    void endToEndDistribution() throws Exception {
        String officer = login("officer"), dealer = login("dealer"), beneficiary = login("beneficiary");

        // 1. officer assigns the seeded card (PHH, 4 members => 10 kg rice + 10 kg wheat) to shop FPS101
        JsonNode card = putJson("/api/officer/ration-cards/1", officer,
                "{\"cardNumber\":\"MH-PUN-000001\",\"headOfFamily\":\"Ramesh Patil\",\"address\":\"Shivajinagar, Pune\","
                        + "\"district\":\"Pune\",\"category\":\"PHH\",\"familyMembers\":4,\"fpsId\":1}", 200);
        assertEquals(10.0, card.get("monthlyEntitlement").get("riceKg").asDouble(), 0.001);

        // 2. dealer sees the full entitlement
        JsonNode bal = getJson("/api/fps/1/cards/MH-PUN-000001/balance", dealer, 200);
        assertEquals(10.0, bal.get("grains").get(0).get("remainingKg").asDouble(), 0.001);

        // 3. issue 5 kg rice
        JsonNode receipt = postJson("/api/fps/1/distributions", dealer,
                "{\"cardNumber\":\"MH-PUN-000001\",\"items\":[{\"grainType\":\"RICE\",\"quantityKg\":5}]}", 201);
        assertTrue(receipt.get("receiptNo").asText().startsWith("RCP-"));

        // 4. 6 kg more would exceed the 5 kg still available
        postJson("/api/fps/1/distributions", dealer,
                "{\"cardNumber\":\"MH-PUN-000001\",\"items\":[{\"grainType\":\"RICE\",\"quantityKg\":6}]}", 409);
        // ...but 5 kg is fine, and uses the entitlement up
        postJson("/api/fps/1/distributions", dealer,
                "{\"cardNumber\":\"MH-PUN-000001\",\"items\":[{\"grainType\":\"RICE\",\"quantityKg\":5}]}", 201);
        postJson("/api/fps/1/distributions", dealer,
                "{\"cardNumber\":\"MH-PUN-000001\",\"items\":[{\"grainType\":\"RICE\",\"quantityKg\":0.5}]}", 409);

        // 5. shop stock fell from 1000 to 990 through ledger entries
        assertEquals(990.0, balance(getJson("/api/fps/1/stock", dealer, 200), "RICE"), 0.001);

        // 6. beneficiary sees the result
        JsonNode ent = getJson("/api/beneficiary/entitlement", beneficiary, 200);
        assertEquals(0.0, ent.get("grains").get(0).get("remainingKg").asDouble(), 0.001);
        assertEquals(2, getJson("/api/beneficiary/distributions", beneficiary, 200).size());

        // 7. dashboard reflects 10 kg issued this month
        assertEquals(10.0, getJson("/api/officer/dashboard/summary", officer, 200).get("distributedThisMonthKg").asDouble(), 0.001);

        // 8. an unassigned or unknown card is refused
        postJson("/api/fps/1/distributions", dealer,
                "{\"cardNumber\":\"DOES-NOT-EXIST\",\"items\":[{\"grainType\":\"RICE\",\"quantityKg\":1}]}", 404);
    }

    @Test
    void complaintsAreAutoClassified() throws Exception {
        String beneficiary = login("beneficiary");
        JsonNode c = postJson("/api/beneficiary/complaints", beneficiary,
                "{\"description\":\"The dealer asked extra money for wheat\"}", 201);
        assertEquals("OVERCHARGING", c.get("category").asText());
        assertEquals("HIGH", c.get("priority").asText());
        assertEquals(1, getJson("/api/beneficiary/complaints", beneficiary, 200).get("items").size());
    }
}
