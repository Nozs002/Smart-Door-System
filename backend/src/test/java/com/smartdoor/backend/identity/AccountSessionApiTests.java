package com.smartdoor.backend.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.smartdoor.backend.identity.dto.LoginRequest;
import com.smartdoor.backend.identity.entity.Account;
import com.smartdoor.backend.identity.repository.AccountRepository;
import com.smartdoor.backend.identity.repository.RevokedAccessTokenRepository;
import com.smartdoor.backend.identity.service.LoginService;
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

@SpringBootTest
@ActiveProfiles("test")
class AccountSessionApiTests {
    @Autowired WebApplicationContext context;
    @Autowired AccountRepository accounts;
    @Autowired RevokedAccessTokenRepository revoked;
    @Autowired LoginService logins;
    @Autowired PasswordEncoder passwords;
    private MockMvc mvc;
    private Account account;
    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        account = new Account();
        account.setUsername("session-" + UUID.randomUUID());
        account.setDisplayName("Session test");
        account.setPasswordHash(passwords.encode("initial-password"));
        account = accounts.saveAndFlush(account);
    }
    private String token(String password) {
        return logins.login(new LoginRequest(account.getUsername(), password)).accessToken();
    }
    @Test
    void changingPasswordInvalidatesEveryEarlierJwtAndRequiresOldPassword() throws Exception {
        String first = token("initial-password"), second = token("initial-password");
        mvc.perform(post("/api/v1/accounts/me/password").header("Authorization", "Bearer " + first)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"wrong\",\"newPassword\":\"next-password\"}"))
                .andExpect(status().isBadRequest());
        assertThat(accounts.findById(account.getId()).orElseThrow().getAuthVersion()).isZero();
        mvc.perform(post("/api/v1/accounts/me/password").header("Authorization", "Bearer " + first)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"initial-password\",\"newPassword\":\"next-password\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.requiresLogin").value(true));
        for (String old : new String[] { first, second }) {
            mvc.perform(get("/api/v1/accounts/me").header("Authorization", "Bearer " + old))
                    .andExpect(status().isUnauthorized());
        }
        String updated = token("next-password");
        mvc.perform(get("/api/v1/accounts/me").header("Authorization", "Bearer " + updated))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.username").value(account.getUsername()));
        Account saved = accounts.findById(account.getId()).orElseThrow();
        assertThat(saved.getAuthVersion()).isEqualTo(1L);
        assertThat(passwords.matches("next-password", saved.getPasswordHash())).isTrue();
    }
    @Test
    void logoutRevokesOnlyTheCurrentTokenAtTheServer() throws Exception {
        String first = token("initial-password"), other = token("initial-password");
        mvc.perform(post("/api/v1/auth/logout").header("Authorization", "Bearer " + first))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.loggedOut").value(true));
        mvc.perform(get("/api/v1/accounts/me").header("Authorization", "Bearer " + first))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/accounts/me").header("Authorization", "Bearer " + other))
                .andExpect(status().isOk());
        assertThat(revoked.findAll()).anyMatch(r -> r.getAccountId().equals(account.getId()));
        assertThat(revoked.findAll()).noneMatch(r -> r.getTokenId().equals(first));
    }
    @Test
    void missingTokenAndInvalidPasswordPolicyCannotMutateAccount() throws Exception {
        mvc.perform(post("/api/v1/accounts/me/password").contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"initial-password\",\"newPassword\":\"next-password\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/accounts/me/password").header("Authorization", "Bearer " + token("initial-password"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"initial-password\",\"newPassword\":\"short\"}"))
                .andExpect(status().isBadRequest());
        assertThat(accounts.findById(account.getId()).orElseThrow().getAuthVersion()).isZero();
    }
}
