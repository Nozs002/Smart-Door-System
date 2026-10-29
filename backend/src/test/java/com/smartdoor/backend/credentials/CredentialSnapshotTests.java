package com.smartdoor.backend.credentials;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.smartdoor.backend.credentials.entity.*;
import com.smartdoor.backend.credentials.repository.*;
import com.smartdoor.backend.credentials.service.CredentialSnapshotService;
import com.smartdoor.backend.door.entity.Door;
import com.smartdoor.backend.door.repository.DoorRepository;
import com.smartdoor.backend.settings.entity.DoorConfig;
import com.smartdoor.backend.settings.repository.DoorConfigRepository;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class CredentialSnapshotTests {
    @Test
    void snapshotContainsOnlyUsableSameOwnerCredentialsAndRedactsItsStringRepresentation() {
        DoorRepository doors = mock(DoorRepository.class);
        CardholderRepository holders = mock(CardholderRepository.class);
        RfidCredentialRepository rfids = mock(RfidCredentialRepository.class);
        SystemPinRepository pins = mock(SystemPinRepository.class);
        DoorConfigRepository configs = mock(DoorConfigRepository.class);
        Clock clock = Clock.fixed(Instant.parse("2026-10-28T00:00:00Z"), ZoneOffset.UTC);
        CredentialSnapshotService service = new CredentialSnapshotService(doors, holders, rfids, pins, configs, clock);
        Door door = new Door(); door.setId(5L); door.setOwnerAccountId(1L);
        DoorConfig config = new DoorConfig(); config.setCredentialsVersion(8L);
        SystemPin pin = new SystemPin(); pin.setPinHash("bcrypt-private-verifier");
        Cardholder active = new Cardholder(); active.setId(10L); active.setAccountId(1L);
        Cardholder disabled = new Cardholder(); disabled.setStatus(Cardholder.Status.DISABLED);
        when(doors.findById(5L)).thenReturn(Optional.of(door));
        when(configs.findById(5L)).thenReturn(Optional.of(config));
        when(pins.findById(5L)).thenReturn(Optional.of(pin));
        when(holders.findByIdAndAccountId(10L, 1L)).thenReturn(Optional.of(active));
        when(holders.findByIdAndAccountId(11L, 1L)).thenReturn(Optional.of(disabled));
        RfidCredential good = credential(1L, 10L, 100L);
        RfidCredential foreign = credential(2L, 20L, 101L);
        RfidCredential revoked = credential(1L, 10L, 102L); revoked.setStatus(RfidCredential.Status.REVOKED);
        RfidCredential expired = credential(1L, 10L, 103L);
        expired.setExpiresAt(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
        RfidCredential disabledHolder = credential(1L, 11L, 104L);
        when(rfids.findAllByDoorId(5L)).thenReturn(List.of(good, foreign, revoked, expired, disabledHolder));
        var snapshot = service.snapshot(5L);
        assertThat(snapshot.version()).isEqualTo(8);
        assertThat(snapshot.pinVerifier()).isEqualTo("bcrypt-private-verifier");
        assertThat(snapshot.cards()).extracting(CredentialSnapshotService.RfidEntry::credentialId)
                .containsExactly(100L);
        assertThat(snapshot.toString()).doesNotContain("bcrypt-private-verifier", "AABBCCDD");
        pin.setStatus(SystemPin.Status.DISABLED);
        assertThat(service.snapshot(5L).pinVerifier()).isNull();
    }

    private RfidCredential credential(Long accountId, Long holderId, Long id) {
        RfidCredential value = new RfidCredential(); value.setId(id); value.setAccountId(accountId);
        value.setDoorId(5L); value.setCardholderId(holderId); value.setUidLookupValue("AABBCCDD");
        return value;
    }
}
