package com.smartdoor.backend.identity.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Stores JWT identifiers only, never the bearer token. */
@Entity
@Table(name = "revoked_access_tokens")
@Getter @Setter @NoArgsConstructor
public class RevokedAccessToken {
    @Id
    @Column(name = "token_id", length = 100, nullable = false, columnDefinition = "varchar(100)")
    private String tokenId;
    @Column(name = "account_id", nullable = false, columnDefinition = "bigint unsigned")
    private Long accountId;
    @Column(name = "expires_at", nullable = false, columnDefinition = "datetime(3)")
    private LocalDateTime expiresAt;
    @Column(name = "revoked_at", nullable = false, columnDefinition = "datetime(3)")
    private LocalDateTime revokedAt;
}
