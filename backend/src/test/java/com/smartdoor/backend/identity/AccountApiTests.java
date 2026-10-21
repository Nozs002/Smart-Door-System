package com.smartdoor.backend.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import com.jayway.jsonpath.JsonPath;
import com.smartdoor.backend.identity.entity.Account;
import com.smartdoor.backend.identity.repository.AccountRepository;

@SpringBootTest
@ActiveProfiles("test")
class AccountApiTests {
    @Autowired WebApplicationContext context;
    @Autowired AccountRepository accounts;
    @Autowired PasswordEncoder passwords;
    private MockMvc mvc;
    private String username;
    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        username = "account-" + UUID.randomUUID();
    }
    private String body(String password) {
        return "{\"displayName\":\"Khách hàng\",\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
    }
    private void register() throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body("test-password")))
                .andExpect(status().isCreated());
    }
    private String login() throws Exception {
        String json = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"test-password\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.data.accessToken");
    }
    @Test
    void registersWithoutTokenAndPersistsOnlyBcrypt() throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body("test-password")))
                .andExpect(status().isCreated()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.username").value(username))
                .andExpect(jsonPath("$.data.createdAt").isString())
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.data.password").doesNotExist())
                .andExpect(jsonPath("$.data.accessToken").doesNotExist());
        Account saved = accounts.findByUsername(username).orElseThrow();
        assertThat(saved.getPasswordHash()).startsWith("$2").isNotEqualTo("test-password");
        assertThat(passwords.matches("test-password", saved.getPasswordHash())).isTrue();
    }
    @Test
    void duplicateUsernameReturns409WithoutOverwritingAccount() throws Exception {
        register();
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body("changed-password")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("CONFLICT"));
        assertThat(passwords.matches("test-password", accounts.findByUsername(username).orElseThrow().getPasswordHash())).isTrue();
    }
    @Test
    void validatesMinimumAndUtf8MaximumPassword() throws Exception {
        for (String password : new String[] { "short", "ấ".repeat(25) }) {
            mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body(password)))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.errors[0].rejectedValue").doesNotExist());
        }
        assertThat(accounts.existsByUsername(username)).isFalse();
    }
    @Test
    void profileUsesAuthenticatedAccountAndRejectsMissingOrDisabledToken() throws Exception {
        register();
        String token = login();
        mvc.perform(get("/api/v1/accounts/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/accounts/me").param("accountId", "999999")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.username").value(username))
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());
        Account saved = accounts.findByUsername(username).orElseThrow();
        saved.setStatus(Account.Status.DISABLED);
        accounts.saveAndFlush(saved);
        mvc.perform(get("/api/v1/accounts/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }
}
