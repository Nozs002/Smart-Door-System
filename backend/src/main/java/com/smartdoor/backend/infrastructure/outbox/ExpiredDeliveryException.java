package com.smartdoor.backend.infrastructure.outbox;

/** A time-limited notification must never be sent after its deadline. */
public class ExpiredDeliveryException extends RuntimeException {
    public ExpiredDeliveryException() { super("Delivery expired"); }
}
