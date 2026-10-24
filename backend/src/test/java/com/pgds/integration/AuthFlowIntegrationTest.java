package com.pgds.integration;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AuthFlowIntegrationTest extends AbstractIntegrationTest {

    private void register(String username, String password) throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\",\"fullName\":\"Test User\"}"))
                .andExpect(status().isCreated());
    }

    private void tryLogin(String user, String pass, int expected) throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + user + "\",\"password\":\"" + pass + "\"}")).andExpect(status().is(expected));
    }

    @Test
    void loginAndProfileWork() throws Exception {
        String token = login("admin");
        var me = getJson("/api/users/me", token, 200);
        assertEquals("SUPER_ADMIN", me.get("role").asText());
    }

    @Test
    void wrongPasswordAndMissingTokenAreRejected() throws Exception {
        tryLogin("admin", "wrong-password", 401);
        mvc.perform(get("/api/users/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/users/me").header("Authorization", "Bearer not-a-real-token")).andExpect(status().isUnauthorized());
    }

    @Test
    void rolesCannotCrossBoundaries() throws Exception {
        getJson("/api/admin/users", login("dealer"), 403);
        getJson("/api/admin/users", login("officer"), 403);
        getJson("/api/officer/dashboard/summary", login("beneficiary"), 403);
        getJson("/api/warehouse/1/stock", login("dealer"), 403);
        getJson("/api/fps/1/stock", login("warehouse"), 403);
        getJson("/api/officer/dashboard/summary", login("officer"), 200);
        getJson("/api/audit/ai/alerts", login("auditor"), 200);
        getJson("/api/officer/ai/alerts", login("auditor"), 403);
    }

    @Test
    void officerCanReviewPossibleBeneficiaryDuplicates() throws Exception {
        String officer = login("officer");
        assertEquals(0, getJson("/api/officer/beneficiaries/possible-duplicates", officer, 200).size());

        var card = postJson("/api/officer/ration-cards", officer,
                "{\"cardNumber\":\"TEST-DUP-0001\",\"headOfFamily\":\"Ramesh Pati\",\"district\":\"Pune\","
                        + "\"category\":\"PHH\",\"familyMembers\":1}", 201);
        postJson("/api/officer/beneficiaries", officer,
                "{\"name\":\"Ramesh Pati\",\"age\":41,\"aadhaarLast4\":\"1234\","
                        + "\"rationCardId\":" + card.get("id").asLong() + "}", 201);

        var matches = getJson("/api/officer/beneficiaries/possible-duplicates", officer, 200);
        assertEquals(1, matches.size());
        assertEquals("XXXX-XXXX-1234", matches.get(0).get("aadhaarMasked").asText());
        assertTrue(matches.get(0).get("nameSimilarityPercent").asInt() >= 85);
        getJson("/api/officer/beneficiaries/possible-duplicates", login("beneficiary"), 403);
    }

    @Test
    void adminCanCreateStaffAndNewStaffCanSignIn() throws Exception {
        String admin = login("admin");
        postJson("/api/admin/users", admin,
                "{\"username\":\"officer2\",\"password\":\"Officer2Pass!\",\"fullName\":\"Second Officer\",\"role\":\"GOVT_OFFICER\"}", 201);
        String t = login("officer2", "Officer2Pass!");
        assertEquals("GOVT_OFFICER", getJson("/api/users/me", t, 200).get("role").asText());
    }

    @Test
    void accountLocksAfterFiveFailuresEvenWithCorrectPassword() throws Exception {
        register("locktest1", "CorrectPass1");
        for (int i = 0; i < 5; i++) tryLogin("locktest1", "bad-password", 401);
        tryLogin("locktest1", "CorrectPass1", 429);
    }

    @Test
    void passwordCanBeChanged() throws Exception {
        register("pwuser1", "OldPassword1");
        String t = login("pwuser1", "OldPassword1");
        postJson("/api/users/me/password", t, "{\"currentPassword\":\"nope\",\"newPassword\":\"NewPassword1\"}", 400);
        postJson("/api/users/me/password", t, "{\"currentPassword\":\"OldPassword1\",\"newPassword\":\"NewPassword1\"}", 204);
        tryLogin("pwuser1", "OldPassword1", 401);
        login("pwuser1", "NewPassword1");
    }
}
