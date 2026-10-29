package com.smartdoor.backend.credentials.service;

import com.smartdoor.backend.shared.error.BadRequestException;
import java.util.Locale;

/** Canonical RC522 UID: 4, 7 or 10 bytes, uppercase hex; separators are presentation only. */
public final class RfidUids {
    private RfidUids() { }
    public static String normalize(String value) {
        String uid = value == null ? "" : value.replaceAll("[\\s:-]", "").toUpperCase(Locale.ROOT);
        if (!uid.matches("(?:[0-9A-F]{8}|[0-9A-F]{14}|[0-9A-F]{20})")) {
            throw new BadRequestException("UID RFID cần 4, 7 hoặc 10 byte dạng hexadecimal.");
        }
        return uid;
    }
}
