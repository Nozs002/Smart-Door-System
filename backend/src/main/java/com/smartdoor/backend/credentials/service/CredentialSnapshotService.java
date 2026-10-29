package com.smartdoor.backend.credentials.service;

import com.smartdoor.backend.credentials.entity.Cardholder;
import com.smartdoor.backend.credentials.entity.RfidCredential;
import com.smartdoor.backend.credentials.entity.SystemPin;
import com.smartdoor.backend.credentials.repository.CardholderRepository;
import com.smartdoor.backend.credentials.repository.RfidCredentialRepository;
import com.smartdoor.backend.credentials.repository.SystemPinRepository;
import com.smartdoor.backend.door.entity.Door;
import com.smartdoor.backend.door.repository.DoorRepository;
import com.smartdoor.backend.settings.repository.DoorConfigRepository;
import com.smartdoor.backend.shared.error.ResourceNotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Sensitive INTERNAL snapshot for an authenticated Node 1 only. Never expose through Account REST. */
@Service
@Transactional(readOnly = true)
public class CredentialSnapshotService {
    private final DoorRepository doors;
    private final CardholderRepository holders;
    private final RfidCredentialRepository rfids;
    private final SystemPinRepository pins;
    private final DoorConfigRepository configs;
    private final Clock clock;
    public CredentialSnapshotService(DoorRepository doors, CardholderRepository holders,
            RfidCredentialRepository rfids, SystemPinRepository pins, DoorConfigRepository configs, Clock clock) {
        this.doors=doors; this.holders=holders; this.rfids=rfids; this.pins=pins; this.configs=configs; this.clock=clock;
    }
    public CredentialsSnapshot snapshot(Long doorId) {
        Door door = doors.findById(doorId).filter(d -> d.getOwnerAccountId() != null)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy cửa."));
        long version = configs.findById(doorId).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy cấu hình cửa."))
                .getCredentialsVersion();
        String verifier = pins.findById(doorId).filter(p -> p.getStatus() == SystemPin.Status.ACTIVE)
                .map(SystemPin::getPinHash).orElse(null);
        List<RfidEntry> entries = rfids.findAllByDoorId(doorId).stream()
                .filter(r -> r.getAccountId().equals(door.getOwnerAccountId()) && r.getStatus() == RfidCredential.Status.ACTIVE)
                .filter(r -> r.getExpiresAt() == null || r.getExpiresAt().toInstant(ZoneOffset.UTC).isAfter(clock.instant()))
                .filter(r -> holders.findByIdAndAccountId(r.getCardholderId(), r.getAccountId())
                        .map(h -> h.getStatus() == Cardholder.Status.ACTIVE).orElse(false))
                .map(r -> new RfidEntry(r.getId(), r.getCardholderId(), r.getUidLookupValue(),
                        r.getExpiresAt() == null ? null : r.getExpiresAt().toInstant(ZoneOffset.UTC))).toList();
        return new CredentialsSnapshot(doorId, version, verifier, entries);
    }
    public record RfidEntry(Long credentialId, Long cardholderId, String uid, Instant expiresAt) { }
    public record CredentialsSnapshot(Long doorId, Long version, String pinVerifier, List<RfidEntry> cards) {
        @Override public String toString() { return "CredentialsSnapshot[doorId="+doorId+", version="+version+", credentials=REDACTED]"; }
    }
}
