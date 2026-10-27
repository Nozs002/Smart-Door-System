package com.smartdoor.backend.identity.dto;

public record LoginResponse(String accessToken, String tokenType, long expiresIn, AccountInfo account) {
    public record AccountInfo(Long id, String username, String displayName) {}
    @Override
    public String toString() { return "LoginResponse[redacted]"; }
}
