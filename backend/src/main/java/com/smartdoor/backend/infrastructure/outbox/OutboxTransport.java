package com.smartdoor.backend.infrastructure.outbox;

/** Delivery succeeds only after the configured transport accepts the message. */
public interface OutboxTransport {
	boolean supports(String kind);
	void deliver(OutboxDelivery message) throws Exception;
}
