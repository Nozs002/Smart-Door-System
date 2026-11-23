package com.smartdoor.backend.onboarding;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles("test")
class TechnicianPortalTests {
    @Autowired WebApplicationContext context;
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void servesLoginPageAndAssetsBeforeAuthentication() throws Exception {
        mvc.perform(get("/technician")).andExpect(status().isOk())
                .andExpect(forwardedUrl("/technician/index.html"));
        mvc.perform(get("/technician/")).andExpect(status().isOk())
                .andExpect(forwardedUrl("/technician/index.html"));
        mvc.perform(get("/technician/index.html")).andExpect(status().isOk())
                .andExpect(content().string(containsString("login-form")));
        mvc.perform(get("/technician/portal.css")).andExpect(status().isOk());
        mvc.perform(get("/technician/portal.js")).andExpect(status().isOk());
    }

    @Test
    void keepsTechnicianOperationsPrivate() throws Exception {
        mvc.perform(get("/api/v1/technician/inventory")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/technician/kits")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/technician/replacements")).andExpect(status().isUnauthorized());
    }
}
