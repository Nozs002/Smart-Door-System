package com.smartdoor.backend.infrastructure.outbox;

/** Internal delivery envelope; payload must never be returned by public status APIs. */
public record OutboxDelivery(Long id, String kind, Long doorId, String deviceCode, String payload) { }
