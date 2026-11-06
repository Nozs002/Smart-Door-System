package com.smartdoor.backend.identity.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import com.smartdoor.backend.identity.dto.LoginRequest;
import com.smartdoor.backend.identity.dto.LoginResponse;
import com.smartdoor.backend.identity.service.LoginRateLimiter;
import com.smartdoor.backend.identity.service.LoginService;
import com.smartdoor.backend.shared.api.ApiResponse;

@RestController
@RequestMapping("/api/v1/auth")
public class LoginController {
    private final LoginService service;
    private final LoginRateLimiter limiter;
    public LoginController(LoginService service, LoginRateLimiter limiter) {
        this.service = service;
        this.limiter = limiter;
    }
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest body,
            HttpServletRequest request, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Pragma", "no-cache");
        limiter.check(body.username(), request.getRemoteAddr());
        return ApiResponse.success("Đăng nhập thành công", service.login(body));
    }
}
