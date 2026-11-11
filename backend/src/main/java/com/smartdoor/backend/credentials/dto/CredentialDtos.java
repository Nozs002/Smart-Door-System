package com.smartdoor.backend.credentials.dto;

import com.smartdoor.backend.credentials.entity.Cardholder;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public final class CredentialDtos {
    private CredentialDtos() { }

    public record CreateCardholder(@NotBlank @Size(max = 100) String displayName) { }
    public record UpdateCardholder(@NotBlank @Size(max = 100) String displayName,
            @NotNull Cardholder.Status status) { }
    public record IssueRfid(@NotNull Long cardholderId, @NotBlank @Size(max = 64) String uid,
            Instant expiresAt) {
        @Override public String toString() { return "IssueRfid[redacted]"; }
    }
    public record UpdatePin(@NotBlank @Pattern(regexp = "[0-9]{4,8}",
            message = "PIN phải gồm 4–8 chữ số.") String pin) {
        @Override public String toString() { return "UpdatePin[redacted]"; }
    }
    public record CardholderView(Long id, String displayName, Cardholder.Status status, Instant createdAt) { }
    public record RfidView(Long id, Long doorId, Long cardholderId, String cardholderName, String uid,
            String status, Instant issuedAt, Instant revokedAt, Instant expiresAt) { }
    public record PinStatus(Long doorId, boolean configured, String status, long version, Instant changedAt) { }
}
