package com.smartdoor.backend.credentials.service;

import com.smartdoor.backend.credentials.entity.Cardholder;
import com.smartdoor.backend.credentials.entity.RfidCredential;
import com.smartdoor.backend.credentials.entity.SystemPin;
import com.smartdoor.backend.credentials.repository.CardholderRepository;
import com.smartdoor.backend.credentials.repository.RfidCredentialRepository;
import com.smartdoor.backend.credentials.repository.SystemPinRepository;
import com.smartdoor.backend.door.entity.Door;
import com.smartdoor.backend.door.repository.DoorRepository;
import com.smartdoor.backend.shared.error.BadRequestException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Internal read port; receiving auth requests and lockout/actuation belong to module access. */
@Service
@Transactional(readOnly = true)
public class CredentialVerificationService {
    private final DoorRepository doors;
    private final CardholderRepository holders;
    private final RfidCredentialRepository rfids;
    private final SystemPinRepository pins;
    private final PasswordEncoder passwords;
    private final Clock clock;
    public CredentialVerificationService(DoorRepository doors, CardholderRepository holders,
            RfidCredentialRepository rfids, SystemPinRepository pins, PasswordEncoder passwords, Clock clock) {
        this.doors=doors; this.holders=holders; this.rfids=rfids; this.pins=pins; this.passwords=passwords; this.clock=clock;
    }
    public VerificationResult verifyRfid(Long doorId, String rawUid) {
        Door door = activeDoor(doorId);
        if (door == null) return denied("DOOR_UNAVAILABLE");
        String uid;
        try { uid = RfidUids.normalize(rawUid); } catch (BadRequestException invalid) { return denied("INVALID_RFID"); }
        RfidCredential credential = rfids.findByDoorIdAndUidLookupValue(doorId, uid).orElse(null);
        if (credential == null || !door.getOwnerAccountId().equals(credential.getAccountId())
                || credential.getStatus() != RfidCredential.Status.ACTIVE
                || (credential.getExpiresAt() != null && !credential.getExpiresAt().isAfter(now()))) {
            return denied("INVALID_RFID");
        }
        Cardholder holder = holders.findByIdAndAccountId(credential.getCardholderId(), credential.getAccountId()).orElse(null);
        if (holder == null || holder.getStatus() != Cardholder.Status.ACTIVE) return denied("INVALID_RFID");
        return new VerificationResult(true, "GRANTED", credential.getId(), holder.getId());
    }
    public VerificationResult verifyPin(Long doorId, String rawPin) {
        if (activeDoor(doorId) == null) return denied("DOOR_UNAVAILABLE");
        SystemPin pin = pins.findById(doorId).orElse(null);
        boolean valid = rawPin != null && rawPin.matches("[0-9]{4,8}") && pin != null
                && pin.getStatus() == SystemPin.Status.ACTIVE && passwords.matches(rawPin, pin.getPinHash());
        return valid ? new VerificationResult(true, "GRANTED", null, null) : denied("INVALID_PIN");
    }
    private Door activeDoor(Long id) {
        if (id == null) return null;
        return doors.findById(id).filter(d -> d.getOwnerAccountId() != null && d.getStatus() == Door.Status.ACTIVE).orElse(null);
    }
    private VerificationResult denied(String reason) { return new VerificationResult(false, reason, null, null); }
    private LocalDateTime now() { return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC); }
    public record VerificationResult(boolean allowed, String reason, Long credentialId, Long cardholderId) { }
}
