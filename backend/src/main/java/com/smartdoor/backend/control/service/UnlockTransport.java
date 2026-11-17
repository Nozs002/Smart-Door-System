package com.smartdoor.backend.control.service;

public interface UnlockTransport {
    boolean connected();
    void publishUnlock(Long doorId, String deviceCode, String commandId, String expiresAt) throws Exception;
    default void publishClose(Long doorId, String deviceCode, String commandId, String expiresAt) throws Exception {
        throw new UnsupportedOperationException("Close commands are not supported by this transport");
    }
}
