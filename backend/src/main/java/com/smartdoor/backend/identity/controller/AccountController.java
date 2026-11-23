package com.smartdoor.backend.identity.controller;

import java.security.Principal;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import com.smartdoor.backend.identity.dto.AccountProfileResponse;
import com.smartdoor.backend.identity.dto.RegisterRequest;
import com.smartdoor.backend.identity.service.AccountService;
import com.smartdoor.backend.shared.api.ApiResponse;

@RestController
public class AccountController {
    private final AccountService accounts;
    public AccountController(AccountService accounts) { this.accounts = accounts; }

    @PostMapping("/api/v1/auth/register")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AccountProfileResponse> register(@Valid @RequestBody RegisterRequest request,
            HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        return ApiResponse.success("Đăng ký tài khoản thành công", accounts.register(request));
    }

    @GetMapping("/api/v1/accounts/me")
    public ApiResponse<AccountProfileResponse> profile(Principal principal, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        return ApiResponse.success("Lấy hồ sơ tài khoản thành công", accounts.profile(principal.getName()));
    }
}
