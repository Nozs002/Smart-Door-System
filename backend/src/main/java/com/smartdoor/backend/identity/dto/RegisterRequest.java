package com.smartdoor.backend.identity.dto;

import java.nio.charset.StandardCharsets;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Size(max = 100) String displayName,
        @NotBlank @Size(max = 100) String username,
        @NotEmpty @Size(max = 72) String password) {

    @AssertTrue(message = "Mật khẩu cần ít nhất 8 ký tự và tối đa 72 byte UTF-8.")
    public boolean isPasswordWithinPolicy() {
        return password == null || (password.codePointCount(0, password.length()) >= 8
                && password.getBytes(StandardCharsets.UTF_8).length <= 72);
    }

    @Override
    public String toString() { return "RegisterRequest[redacted]"; }
}
