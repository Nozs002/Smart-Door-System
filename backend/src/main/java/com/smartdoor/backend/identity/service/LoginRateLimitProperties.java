package com.smartdoor.backend.identity.service;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Validated
@ConfigurationProperties("app.login-rate-limit")
public record LoginRateLimitProperties(@DefaultValue("5") @Min(1) int usernameAttempts,
        @DefaultValue("30") @Min(1) int ipAttempts,
        @DefaultValue("60s") @NotNull Duration window,
        @DefaultValue("10000") @Min(2) int maxKeys) {
    @AssertTrue(message = "Rate limit window must be positive")
    public boolean isWindowPositive() { return window != null && !window.isNegative() && !window.isZero(); }
}
