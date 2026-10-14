package com.smartdoor.backend.identity;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import com.smartdoor.backend.identity.controller.LoginController;
import com.smartdoor.backend.identity.dto.LoginResponse;
import com.smartdoor.backend.identity.service.*;
import com.smartdoor.backend.shared.api.ApiResponseAdvice;
import com.smartdoor.backend.shared.error.*;
import com.smartdoor.backend.shared.observability.CorrelationIdFilter;

class LoginControllerTests {
    private final LoginService service = mock(LoginService.class);
    private final LoginRateLimiter limiter = mock(LoginRateLimiter.class);
    private MockMvc mvc;
    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.standaloneSetup(new LoginController(service, limiter))
                .setControllerAdvice(new ApiResponseAdvice(), new GlobalExceptionHandler())
                .addFilters(new CorrelationIdFilter()).build();
    }
    @Test
    void returnsSafeWrappedResponseWithoutCaching() throws Exception {
        when(service.login(any())).thenReturn(new LoginResponse("test-token-placeholder", "Bearer", 900,
                new LoginResponse.AccountInfo(7L, "customer", "Khách hàng")));
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .header("X-Correlation-ID", "login-test").content("{\"username\":\"customer\",\"password\":\"test\"}"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.expiresIn").value(900))
                .andExpect(jsonPath("$.data.account.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.correlationId").value("login-test"));
    }
    @Test
    void rejectsOversizeUtf8PasswordBeforeService() throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"customer\",\"password\":\"" + "ấ".repeat(25) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].rejectedValue").doesNotExist());
        verifyNoInteractions(service, limiter);
    }
    @Test
    void rateLimitedRequestDoesNotReachCredentialService() throws Exception {
        doThrow(new ApplicationException(ErrorCode.LOGIN_RATE_LIMITED, "Vui lòng chờ."))
                .when(limiter).check(any(), any());
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"customer\",\"password\":\"test\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.errorCode").value("LOGIN_RATE_LIMITED"))
                .andExpect(jsonPath("$.title").value("Quá nhiều lần đăng nhập"));
        verifyNoInteractions(service);
    }
}
