package com.smartdoor.backend.identity.dto;

import java.time.Instant;
import java.time.ZoneOffset;
import com.smartdoor.backend.identity.entity.Account;

public record AccountProfileResponse(Long id, String username, String displayName,
        Account.Status status, Instant createdAt) {
    public static AccountProfileResponse from(Account account) {
        return new AccountProfileResponse(account.getId(), account.getUsername(), account.getDisplayName(),
                account.getStatus(), account.getCreatedAt().toInstant(ZoneOffset.UTC));
    }
}
