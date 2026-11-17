-- Backend revocation alone cannot prove an established MQTT session was removed.
ALTER TABLE device_transport_credentials
    ADD COLUMN broker_revoked_at DATETIME(3) NULL,
    ADD CONSTRAINT chk_broker_revocation_after_key_revocation
        CHECK (broker_revoked_at IS NULL OR
            (revoked_at IS NOT NULL AND broker_revoked_at >= revoked_at));
