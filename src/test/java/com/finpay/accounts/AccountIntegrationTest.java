package com.finpay.accounts;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "finpay.security.jwt.secret=finpay-test-secret-key-32-bytes!!")
@AutoConfigureMockMvc
@Transactional
class AccountIntegrationTest {

    private static final String PASSWORD = "P@ssw0rd-123!";
    private static final Pattern ACCESS_TOKEN = Pattern.compile("\\\"accessToken\\\":\\\"([^\\\"]+)\\\"");

    @Autowired
    private MockMvc mockMvc;

    @Test
    void ownerCanCreateReadAndChangeAccountStatus() throws Exception {
        String token = createUserAndLogin();
        MvcResult created = mockMvc.perform(post("/api/accounts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currency\":\"eur\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.currency").value("EUR"))
                .andExpect(jsonPath("$.balance").value(0))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.version").value(0))
                .andReturn();
        String accountId = UUID.fromString(created.getResponse().getContentAsString()
                .replaceAll(".*\\\"id\\\":\\\"([^\\\"]+)\\\".*", "$1")).toString();

        mockMvc.perform(get("/api/accounts").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(accountId));
        mockMvc.perform(get("/api/accounts/{id}", accountId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(accountId));
        mockMvc.perform(get("/api/accounts/{id}/balance", accountId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.balance").value(0));
        mockMvc.perform(patch("/api/accounts/{id}/status", accountId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"BLOCKED\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("BLOCKED"))
                .andExpect(jsonPath("$.version").value(1));
        mockMvc.perform(patch("/api/accounts/{id}/status", accountId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"CLOSED\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CLOSED"));
        mockMvc.perform(patch("/api/accounts/{id}/status", accountId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_ACCOUNT_STATUS_TRANSITION"));
    }

    @Test
    void accountIsPrivateAndCurrencyMustBeSupported() throws Exception {
        String owner = createUserAndLogin();
        MvcResult created = mockMvc.perform(post("/api/accounts")
                        .header("Authorization", "Bearer " + owner).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currency\":\"USD\"}"))
                .andExpect(status().isCreated()).andReturn();
        String accountId = created.getResponse().getContentAsString().replaceAll(".*\\\"id\\\":\\\"([^\\\"]+)\\\".*", "$1");

        String otherUser = createUserAndLogin();
        mockMvc.perform(get("/api/accounts/{id}", accountId).header("Authorization", "Bearer " + otherUser))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/accounts").header("Authorization", "Bearer " + otherUser))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
        mockMvc.perform(post("/api/accounts").header("Authorization", "Bearer " + otherUser)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"currency\":\"ZZZ\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_CURRENCY"));
    }

    @Test
    void accountsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/accounts")).andExpect(status().isUnauthorized());
    }

    private String createUserAndLogin() throws Exception {
        String email = "finpay+" + UUID.randomUUID() + "@example.test";
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isCreated());
        String body = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var matcher = ACCESS_TOKEN.matcher(body);
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }
}
