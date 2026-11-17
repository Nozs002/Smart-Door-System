package com.smartdoor.backend.credentials;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.smartdoor.backend.credentials.dto.CredentialDtos.*;
import com.smartdoor.backend.credentials.entity.*;
import com.smartdoor.backend.credentials.repository.*;
import com.smartdoor.backend.credentials.service.*;
import com.smartdoor.backend.door.service.DoorOwnershipService;
import com.smartdoor.backend.identity.entity.Account;
import com.smartdoor.backend.identity.repository.AccountRepository;
import com.smartdoor.backend.settings.service.DeviceCommandPublisher.ChangeReason;
import com.smartdoor.backend.settings.service.DoorSettingsService;
import com.smartdoor.backend.shared.error.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class CredentialsManagementTests {
    private final AccountRepository accounts = mock(AccountRepository.class);
    private final CardholderRepository holders = mock(CardholderRepository.class);
    private final RfidCredentialRepository rfids = mock(RfidCredentialRepository.class);
    private final SystemPinRepository pins = mock(SystemPinRepository.class);
    private final DoorOwnershipService ownership = mock(DoorOwnershipService.class);
    private final DoorSettingsService settings = mock(DoorSettingsService.class);
    private final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder();
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-28T00:00:00Z"), ZoneOffset.UTC);
    private final CredentialsManagementService service = new CredentialsManagementService(
            accounts, holders, rfids, pins, ownership, settings, passwords, clock);
    private Cardholder holder;
    @BeforeEach
    void setup() {
        Account account = new Account(); account.setId(1L);
        when(accounts.findById(1L)).thenReturn(Optional.of(account));
        when(accounts.findByIdForUpdate(1L)).thenReturn(Optional.of(account));
        holder = new Cardholder(); holder.setId(10L); holder.setAccountId(1L); holder.setDisplayName("Member");
        when(holders.findOwnedForUpdate(10L, 1L)).thenReturn(Optional.of(holder));
        when(holders.findByIdAndAccountId(10L, 1L)).thenReturn(Optional.of(holder));
        when(holders.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
        when(rfids.saveAndFlush(any())).thenAnswer(i -> { RfidCredential r=i.getArgument(0); r.setId(30L); return r; });
        when(pins.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
    }
    @Test
    void issuingChecksDoorOwnerAndCardholderScopeBeforeSaving() {
        when(holders.findOwnedForUpdate(99L, 1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.issueRfid(1L, 5L, new IssueRfid(99L, "AA-BB-CC-DD", null)))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(ownership).requireOwnerForUpdate(1L, 5L);
        verify(rfids, never()).saveAndFlush(any());
        verifyNoInteractions(settings);
    }
    @Test
    void grantNormalizesUidAndRequestsCredentialSyncInSameWorkflow() {
        RfidView view = service.issueRfid(1L, 5L, new IssueRfid(10L, "aa:bb:cc:dd", clock.instant().plusSeconds(60)));
        assertThat(view.uid()).isEqualTo("AABBCCDD");
        assertThat(view.cardholderId()).isEqualTo(10L);
        assertThat(view.status()).isEqualTo("ACTIVE");
        verify(settings).requestCredentialsSync(1L, 5L, ChangeReason.RFID_CHANGED);
        var order = inOrder(accounts, ownership, holders, rfids);
        order.verify(accounts).findByIdForUpdate(1L);
        order.verify(ownership).requireOwnerForUpdate(1L, 5L);
        order.verify(holders).findOwnedForUpdate(10L, 1L);
    }
    @Test
    void rejectsDisabledHolderExpiredIssueAndChangingTheHistoricalHolder() {
        holder.setStatus(Cardholder.Status.DISABLED);
        assertThatThrownBy(() -> service.issueRfid(1L, 5L, new IssueRfid(10L,"AABBCCDD",null)))
                .isInstanceOf(ConflictException.class);
        holder.setStatus(Cardholder.Status.ACTIVE);
        assertThatThrownBy(() -> service.issueRfid(1L, 5L, new IssueRfid(10L,"AABBCCDD",clock.instant())))
                .isInstanceOf(BadRequestException.class);
        RfidCredential previous = new RfidCredential(); previous.setCardholderId(77L); previous.setStatus(RfidCredential.Status.REVOKED);
        when(rfids.findByDoorIdAndUidLookupValue(5L,"AABBCCDD")).thenReturn(Optional.of(previous));
        assertThatThrownBy(() -> service.issueRfid(1L, 5L, new IssueRfid(10L,"AABBCCDD",null)))
                .isInstanceOf(ConflictException.class);
        verify(rfids, never()).saveAndFlush(any());
    }
    @Test
    void revokeIsScopedAndRetryDoesNotIssueAnotherVersion() {
        RfidCredential card = new RfidCredential(); card.setId(30L); card.setDoorId(5L); card.setAccountId(1L);
        card.setCardholderId(10L); card.setUidLookupValue("AABBCCDD");
        when(rfids.findByIdAndDoorIdAndAccountId(30L,5L,1L)).thenReturn(Optional.of(card));
        service.revokeRfid(1L,5L,30L);
        service.revokeRfid(1L,5L,30L);
        assertThat(card.getStatus()).isEqualTo(RfidCredential.Status.REVOKED);
        assertThat(card.getRevokedAt()).isNotNull();
        verify(settings,times(1)).requestCredentialsSync(1L,5L,ChangeReason.RFID_REVOKED);
    }
    @Test
    void pinIsBcryptOnlyInPersistenceAndViewNeverContainsIt() {
        PinStatus status=service.updatePin(1L,5L,new UpdatePin("123456"));
        var saved=org.mockito.ArgumentCaptor.forClass(SystemPin.class);
        verify(pins).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getPinHash()).isNotEqualTo("123456").startsWith("$2");
        assertThat(passwords.matches("123456",saved.getValue().getPinHash())).isTrue();
        assertThat(status.configured()).isTrue();
        assertThat(status.toString()).doesNotContain("123456",saved.getValue().getPinHash());
        verify(settings).requestCredentialsSync(1L,5L,ChangeReason.PIN_CHANGED);
    }
    @Test
    void disablingHolderLocksAllAffectedDoorsAndRequestsRevocationSnapshot() {
        RfidCredential first=new RfidCredential(); first.setDoorId(9L);
        RfidCredential second=new RfidCredential(); second.setDoorId(3L);
        when(rfids.findAllByCardholderIdAndAccountId(10L,1L)).thenReturn(List.of(first,second));
        service.updateCardholder(1L,10L,new UpdateCardholder("Member",Cardholder.Status.DISABLED));
        assertThat(first.getStatus()).isEqualTo(RfidCredential.Status.REVOKED);
        assertThat(second.getStatus()).isEqualTo(RfidCredential.Status.REVOKED);
        assertThat(first.getRevokedAt()).isNotNull();
        var order=inOrder(ownership);
        order.verify(ownership).requireOwnerForUpdate(1L,3L);
        order.verify(ownership).requireOwnerForUpdate(1L,9L);
        verify(settings).requestCredentialsSync(1L,3L,ChangeReason.RFID_CHANGED);
        verify(settings).requestCredentialsSync(1L,9L,ChangeReason.RFID_CHANGED);
        service.updateCardholder(1L,10L,new UpdateCardholder("Member",Cardholder.Status.ACTIVE));
        assertThat(first.getStatus()).isEqualTo(RfidCredential.Status.REVOKED);
        assertThat(second.getStatus()).isEqualTo(RfidCredential.Status.REVOKED);
    }
}
