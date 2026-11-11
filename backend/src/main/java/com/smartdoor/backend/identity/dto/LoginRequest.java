package com.smartdoor.backend.identity.dto;

import java.nio.charset.StandardCharsets;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

public record LoginRequest(@NotBlank @Size(max = 100) String username,
        @NotEmpty @Size(max = 72) String password) {
    @AssertTrue(message = "Mật khẩu không được vượt quá 72 byte UTF-8.")
    public boolean isPasswordWithinByteLimit() {
        return password == null || password.getBytes(StandardCharsets.UTF_8).length <= 72;
    }

    @Override
    public String toString() { return "LoginRequest[redacted]"; }
}
