package com.smartdoor.backend.credentials;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.smartdoor.backend.credentials.entity.*;
import com.smartdoor.backend.credentials.repository.*;
import com.smartdoor.backend.credentials.service.*;
import com.smartdoor.backend.door.entity.Door;
import com.smartdoor.backend.door.repository.DoorRepository;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class CredentialVerificationTests {
    private final DoorRepository doors=mock(DoorRepository.class);
    private final CardholderRepository holders=mock(CardholderRepository.class);
    private final RfidCredentialRepository rfids=mock(RfidCredentialRepository.class);
    private final SystemPinRepository pins=mock(SystemPinRepository.class);
    private final Clock clock=Clock.fixed(Instant.parse("2026-10-28T00:00:00Z"),ZoneOffset.UTC);
    private final BCryptPasswordEncoder passwords=new BCryptPasswordEncoder();
    private final CredentialVerificationService service=new CredentialVerificationService(doors,holders,rfids,pins,passwords,clock);
    @Test
    void verifiesDoorScopeStatusHolderAndExpiry() {
        Door door=new Door(); door.setId(5L); door.setOwnerAccountId(1L); door.setStatus(Door.Status.ACTIVE);
        when(doors.findById(5L)).thenReturn(Optional.of(door));
        Cardholder holder=new Cardholder(); holder.setId(10L); holder.setAccountId(1L);
        when(holders.findByIdAndAccountId(10L,1L)).thenReturn(Optional.of(holder));
        RfidCredential card=new RfidCredential(); card.setId(30L); card.setAccountId(1L); card.setCardholderId(10L);
        when(rfids.findByDoorIdAndUidLookupValue(5L,"AABBCCDD")).thenReturn(Optional.of(card));
        assertThat(service.verifyRfid(5L,"aa-bb-cc-dd").allowed()).isTrue();
        holder.setStatus(Cardholder.Status.DISABLED);
        assertThat(service.verifyRfid(5L,"AABBCCDD").allowed()).isFalse();
        holder.setStatus(Cardholder.Status.ACTIVE);
        card.setExpiresAt(LocalDateTime.ofInstant(clock.instant(),ZoneOffset.UTC));
        assertThat(service.verifyRfid(5L,"AABBCCDD").allowed()).isFalse();
        card.setExpiresAt(null); card.setAccountId(999L);
        assertThat(service.verifyRfid(5L,"AABBCCDD").allowed()).isFalse();
        assertThat(service.verifyRfid(999L,"AABBCCDD").allowed()).isFalse();
    }
    @Test
    void pinNeedsActiveDoorAndActiveVerifier() {
        Door door=new Door(); door.setOwnerAccountId(1L); door.setStatus(Door.Status.ACTIVE);
        when(doors.findById(5L)).thenReturn(Optional.of(door));
        SystemPin pin=new SystemPin(); pin.setPinHash(passwords.encode("1234"));
        when(pins.findById(5L)).thenReturn(Optional.of(pin));
        assertThat(service.verifyPin(5L,"1234").allowed()).isTrue();
        assertThat(service.verifyPin(5L,"0000").allowed()).isFalse();
        door.setStatus(Door.Status.DISABLED);
        assertThat(service.verifyPin(5L,"1234").allowed()).isFalse();
    }
}
