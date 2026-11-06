package com.smartdoor.backend.identity.controller;

import com.smartdoor.backend.identity.dto.ChangePasswordRequest;
import com.smartdoor.backend.identity.service.AccountSessionService;
import com.smartdoor.backend.shared.api.ApiResponse;
import com.smartdoor.backend.shared.security.AccountPrincipal;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AccountSessionController {
    private final AccountSessionService sessions;
    public AccountSessionController(AccountSessionService sessions) { this.sessions = sessions; }

    @PostMapping("/api/v1/accounts/me/password")
    public ApiResponse<Map<String, Boolean>> password(@AuthenticationPrincipal AccountPrincipal principal,
            @Valid @RequestBody ChangePasswordRequest request) {
        sessions.changePassword(principal, request);
        return ApiResponse.success("Đã đổi mật khẩu. Vui lòng đăng nhập lại.", Map.of("requiresLogin", true));
    }

    @PostMapping("/api/v1/auth/logout")
    public ApiResponse<Map<String, Boolean>> logout(@AuthenticationPrincipal AccountPrincipal principal) {
        sessions.logout(principal);
        return ApiResponse.success("Đã đăng xuất phiên hiện tại.", Map.of("loggedOut", true));
    }
}
