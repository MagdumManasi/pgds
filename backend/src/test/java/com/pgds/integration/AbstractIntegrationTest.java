package com.pgds.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Full application against an in-memory H2 database, seeded with the demo accounts (password Pgds@12345). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
abstract class AbstractIntegrationTest {

    static final String DEMO_PASSWORD = "Pgds@12345";

    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper json;

    protected String login(String user, String password) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + user + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("token").asText();
    }

    protected String login(String demoUser) throws Exception { return login(demoUser, DEMO_PASSWORD); }

    protected JsonNode getJson(String url, String token, int expectedStatus) throws Exception {
        MvcResult r = mvc.perform(get(url).header("Authorization", "Bearer " + token)).andExpect(status().is(expectedStatus)).andReturn();
        String body = r.getResponse().getContentAsString();
        return body.isBlank() ? json.createObjectNode() : json.readTree(body);
    }

    protected JsonNode postJson(String url, String token, String body, int expectedStatus) throws Exception {
        MvcResult r = mvc.perform(post(url).header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(body == null ? "" : body)).andExpect(status().is(expectedStatus)).andReturn();
        String out = r.getResponse().getContentAsString();
        return out.isBlank() ? json.createObjectNode() : json.readTree(out);
    }

    protected JsonNode putJson(String url, String token, String body, int expectedStatus) throws Exception {
        MvcResult r = mvc.perform(put(url).header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().is(expectedStatus)).andReturn();
        return json.readTree(r.getResponse().getContentAsString());
    }

    /** Balance of one grain from a StockSummary JSON. */
    protected double balance(JsonNode summary, String grain) {
        for (JsonNode b : summary.get("balances")) if (grain.equals(b.get("grainType").asText())) return b.get("quantityKg").asDouble();
        throw new AssertionError("no balance for " + grain);
    }
}
