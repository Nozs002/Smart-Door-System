-- Account authentication version invalidates every older JWT after a password change.
-- Existing accounts retain version 0; tokens without the new claim require sign-in again.
ALTER TABLE accounts
    ADD COLUMN auth_version BIGINT UNSIGNED NOT NULL DEFAULT 0 AFTER password_hash;

-- A revoked JWT is identified by jti only. No bearer token/password is persisted here.
CREATE TABLE revoked_access_tokens (
    token_id VARCHAR(100) NOT NULL PRIMARY KEY,
    account_id BIGINT UNSIGNED NOT NULL,
    expires_at DATETIME(3) NOT NULL,
    revoked_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    KEY idx_revoked_token_expiry (expires_at),
    CONSTRAINT fk_revoked_token_account FOREIGN KEY (account_id) REFERENCES accounts(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
