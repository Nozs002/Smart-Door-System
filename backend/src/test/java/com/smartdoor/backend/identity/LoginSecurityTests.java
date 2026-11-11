package com.smartdoor.backend.identity;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import com.smartdoor.backend.identity.dto.LoginRequest;
import com.smartdoor.backend.identity.entity.Account;
import com.smartdoor.backend.identity.repository.AccountRepository;
import com.smartdoor.backend.identity.service.LoginService;

@SpringBootTest
@ActiveProfiles("test")
class LoginSecurityTests {
    @Autowired WebApplicationContext context;
    @Autowired PasswordEncoder passwords;
    @Autowired LoginService service;
    @MockitoBean AccountRepository accounts;
    private MockMvc mvc;
    private Account account;
    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        account = new Account();
        account.setId(7L);
        account.setUsername("security-test");
        account.setDisplayName("Test");
        account.setPasswordHash(passwords.encode("test-password"));
        when(accounts.findByUsername(anyString())).thenReturn(Optional.of(account));
        when(accounts.findById(7L)).thenReturn(Optional.of(account));
    }
    @Test
    void loginIsPublicAndWrongPasswordReturnsCommon401() throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"security-test\",\"password\":\"test-password\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.accessToken").isString());
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"security-test\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.errorCode").value("INVALID_CREDENTIALS"));
    }
    @Test
    void protectedEndpointRequiresValidActiveAccountToken() throws Exception {
        mvc.perform(get("/actuator/info")).andExpect(status().isUnauthorized());
        String token = service.login(new LoginRequest("security-test", "test-password")).accessToken();
        mvc.perform(get("/actuator/info").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        account.setStatus(Account.Status.DISABLED);
        mvc.perform(get("/actuator/info").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.errorCode").value("AUTHENTICATION_REQUIRED"));
        mvc.perform(get("/actuator/info").header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized());
    }
}
