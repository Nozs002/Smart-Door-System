package com.smartdoor.backend.identity.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.nio.charset.StandardCharsets;

public record ChangePasswordRequest(@NotEmpty @Size(max = 72) String currentPassword,
        @NotEmpty @Size(max = 72) String newPassword) {
    @AssertTrue(message = "Mật khẩu mới cần ít nhất 8 ký tự và tối đa 72 byte UTF-8.")
    public boolean isNewPasswordWithinPolicy() {
        return newPassword == null || (newPassword.codePointCount(0, newPassword.length()) >= 8
                && newPassword.getBytes(StandardCharsets.UTF_8).length <= 72);
    }
    @AssertTrue(message = "Mật khẩu hiện tại không được vượt quá 72 byte UTF-8.")
    public boolean isCurrentPasswordWithinPolicy() {
        return currentPassword == null || currentPassword.getBytes(StandardCharsets.UTF_8).length <= 72;
    }
    @Override public String toString() { return "ChangePasswordRequest[redacted]"; }
}
