package com.smartdoor.backend.credentials.service;

import com.smartdoor.backend.credentials.dto.CredentialDtos.*;
import com.smartdoor.backend.credentials.entity.Cardholder;
import com.smartdoor.backend.credentials.entity.RfidCredential;
import com.smartdoor.backend.credentials.entity.SystemPin;
import com.smartdoor.backend.credentials.repository.CardholderRepository;
import com.smartdoor.backend.credentials.repository.RfidCredentialRepository;
import com.smartdoor.backend.credentials.repository.SystemPinRepository;
import com.smartdoor.backend.door.service.DoorOwnershipService;
import com.smartdoor.backend.identity.entity.Account;
import com.smartdoor.backend.identity.repository.AccountRepository;
import com.smartdoor.backend.settings.service.DeviceCommandPublisher.ChangeReason;
import com.smartdoor.backend.settings.service.DoorSettingsService;
import com.smartdoor.backend.shared.api.PageResponse;
import com.smartdoor.backend.shared.error.AuthenticationRequiredException;
import com.smartdoor.backend.shared.error.BadRequestException;
import com.smartdoor.backend.shared.error.ConflictException;
import com.smartdoor.backend.shared.error.ResourceNotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Credential writers serialize Account -> sorted Doors -> credential entities. */
@Service
@Transactional(readOnly = true)
public class CredentialsManagementService {
    private final AccountRepository accounts;
    private final CardholderRepository cardholders;
    private final RfidCredentialRepository rfids;
    private final SystemPinRepository pins;
    private final DoorOwnershipService ownership;
    private final DoorSettingsService settings;
    private final PasswordEncoder passwords;
    private final Clock clock;

    public CredentialsManagementService(AccountRepository accounts, CardholderRepository cardholders,
            RfidCredentialRepository rfids, SystemPinRepository pins, DoorOwnershipService ownership,
            DoorSettingsService settings, PasswordEncoder passwords, Clock clock) {
        this.accounts = accounts; this.cardholders = cardholders; this.rfids = rfids; this.pins = pins;
        this.ownership = ownership; this.settings = settings; this.passwords = passwords; this.clock = clock;
    }

    public PageResponse<CardholderView> listCardholders(Long accountId, int page, int size) {
        activeAccount(accountId, false);
        return PageResponse.from(cardholders.findAllByAccountId(accountId, PageResponse.pageable(page, size))
                .map(this::cardholderView));
    }

    @Transactional
    public CardholderView createCardholder(Long accountId, CreateCardholder request) {
        activeAccount(accountId, true);
        validateName(request == null ? null : request.displayName());
        Cardholder holder = new Cardholder();
        holder.setAccountId(accountId);
        holder.setDisplayName(request.displayName().strip());
        holder.setCreatedAt(now());
        return cardholderView(cardholders.saveAndFlush(holder));
    }

    @Transactional
    public CardholderView updateCardholder(Long accountId, Long cardholderId, UpdateCardholder request) {
        activeAccount(accountId, true);
        validateName(request == null ? null : request.displayName());
        if (request.status() == null) throw new BadRequestException("Vui lòng chọn trạng thái người giữ thẻ.");
        List<RfidCredential> affectedCards = rfids.findAllByCardholderIdAndAccountId(cardholderId, accountId);
        List<Long> doorIds = affectedCards.stream()
                .map(RfidCredential::getDoorId).distinct().sorted().toList();
        doorIds.forEach(id -> ownership.requireOwnerForUpdate(accountId, id));
        Cardholder holder = cardholders.findOwnedForUpdate(cardholderId, accountId).orElseThrow(this::notFound);
        boolean permissionChanged = holder.getStatus() != request.status();
        holder.setDisplayName(request.displayName().strip());
        holder.setStatus(request.status());
        cardholders.saveAndFlush(holder);
        if (request.status() == Cardholder.Status.DISABLED) {
            for (RfidCredential card : affectedCards) {
                if (card.getStatus() != RfidCredential.Status.REVOKED) {
                    card.setStatus(RfidCredential.Status.REVOKED);
                    card.setRevokedAt(now());
                }
            }
            rfids.saveAll(affectedCards);
        }
        if (permissionChanged) {
            doorIds.forEach(id -> settings.requestCredentialsSync(accountId, id, ChangeReason.RFID_CHANGED));
        }
        return cardholderView(holder);
    }

    public PageResponse<RfidView> listRfid(Long accountId, Long doorId, int page, int size) {
        activeAccount(accountId, false);
        ownership.requireOwner(accountId, doorId);
        return PageResponse.from(rfids.findAllByDoorIdAndAccountId(doorId, accountId, PageResponse.pageable(page, size))
                .map(this::rfidView));
    }

    @Transactional
    public RfidView issueRfid(Long accountId, Long doorId, IssueRfid request) {
        activeAccount(accountId, true);
        ownership.requireOwnerForUpdate(accountId, doorId);
        if (request == null || request.cardholderId() == null) throw new BadRequestException("Thiếu người giữ thẻ.");
        Cardholder holder = cardholders.findOwnedForUpdate(request.cardholderId(), accountId).orElseThrow(this::notFound);
        if (holder.getStatus() != Cardholder.Status.ACTIVE) {
            throw new ConflictException("Người giữ thẻ đã bị vô hiệu hóa.");
        }
        String uid = RfidUids.normalize(request.uid());
        if (request.expiresAt() != null && !request.expiresAt().isAfter(clock.instant())) {
            throw new BadRequestException("Thời hạn RFID phải ở tương lai.");
        }
        RfidCredential credential = rfids.findByDoorIdAndUidLookupValue(doorId, uid).orElse(null);
        if (credential != null) {
            if (!credential.getCardholderId().equals(holder.getId())) {
                throw new ConflictException("UID đã được gắn với người giữ thẻ khác tại cửa này; không chuyển để giữ lịch sử.");
            }
            if (usable(credential)) throw new ConflictException("Thẻ này đang được cấp tại cửa.");
        } else {
            credential = new RfidCredential();
            credential.setAccountId(accountId);
            credential.setDoorId(doorId);
            credential.setCardholderId(holder.getId());
            credential.setUidLookupValue(uid);
        }
        credential.setStatus(RfidCredential.Status.ACTIVE);
        credential.setIssuedAt(now());
        credential.setRevokedAt(null);
        credential.setExpiresAt(request.expiresAt() == null ? null : LocalDateTime.ofInstant(request.expiresAt(), ZoneOffset.UTC));
        credential = rfids.saveAndFlush(credential);
        settings.requestCredentialsSync(accountId, doorId, ChangeReason.RFID_CHANGED);
        return rfidView(credential);
    }

    @Transactional
    public RfidView revokeRfid(Long accountId, Long doorId, Long credentialId) {
        activeAccount(accountId, true);
        ownership.requireOwnerForUpdate(accountId, doorId);
        RfidCredential credential = rfids.findByIdAndDoorIdAndAccountId(credentialId, doorId, accountId)
                .orElseThrow(this::notFound);
        if (credential.getStatus() != RfidCredential.Status.REVOKED) {
            credential.setStatus(RfidCredential.Status.REVOKED);
            credential.setRevokedAt(now());
            rfids.saveAndFlush(credential);
            settings.requestCredentialsSync(accountId, doorId, ChangeReason.RFID_REVOKED);
        }
        return rfidView(credential);
    }

    public PinStatus pinStatus(Long accountId, Long doorId) {
        activeAccount(accountId, false);
        ownership.requireOwner(accountId, doorId);
        return pinView(doorId, pins.findById(doorId).orElse(null));
    }

    @Transactional
    public PinStatus updatePin(Long accountId, Long doorId, UpdatePin request) {
        activeAccount(accountId, true);
        ownership.requireOwnerForUpdate(accountId, doorId);
        if (request == null || request.pin() == null || !request.pin().matches("[0-9]{4,8}")) {
            throw new BadRequestException("PIN phải gồm 4–8 chữ số.");
        }
        SystemPin pin = pins.findById(doorId).orElse(null);
        if (pin == null) {
            pin = new SystemPin();
            pin.setDoorId(doorId);
        } else {
            pin.setVersion(pin.getVersion() + 1);
        }
        pin.setPinHash(passwords.encode(request.pin()));
        pin.setStatus(SystemPin.Status.ACTIVE);
        pin.setChangedAt(now());
        pins.saveAndFlush(pin);
        settings.requestCredentialsSync(accountId, doorId, ChangeReason.PIN_CHANGED);
        return pinView(doorId, pin);
    }

    private void activeAccount(Long id, boolean lock) {
        if (id == null) throw new AuthenticationRequiredException();
        Account account = (lock ? accounts.findByIdForUpdate(id) : accounts.findById(id))
                .orElseThrow(AuthenticationRequiredException::new);
        if (account.getStatus() != Account.Status.ACTIVE) throw new AuthenticationRequiredException();
    }

    private void validateName(String value) {
        if (value == null || value.isBlank() || value.length() > 100) {
            throw new BadRequestException("Tên người giữ thẻ bắt buộc, tối đa 100 ký tự.");
        }
    }
    private boolean usable(RfidCredential value) {
        return value.getStatus() == RfidCredential.Status.ACTIVE
                && (value.getExpiresAt() == null || value.getExpiresAt().isAfter(now()));
    }
    private CardholderView cardholderView(Cardholder value) {
        return new CardholderView(value.getId(), value.getDisplayName(), value.getStatus(), utc(value.getCreatedAt()));
    }
    private RfidView rfidView(RfidCredential value) {
        String name = cardholders.findByIdAndAccountId(value.getCardholderId(), value.getAccountId())
                .map(Cardholder::getDisplayName).orElse("Người giữ thẻ");
        String status = value.getStatus() == RfidCredential.Status.ACTIVE && !usable(value)
                ? RfidCredential.Status.EXPIRED.name() : value.getStatus().name();
        return new RfidView(value.getId(), value.getDoorId(), value.getCardholderId(), name,
                value.getUidLookupValue(), status, utc(value.getIssuedAt()), utc(value.getRevokedAt()), utc(value.getExpiresAt()));
    }
    private PinStatus pinView(Long doorId, SystemPin value) {
        return value == null ? new PinStatus(doorId, false, "NOT_CONFIGURED", 0, null)
                : new PinStatus(doorId, true, value.getStatus().name(), value.getVersion(), utc(value.getChangedAt()));
    }
    private ResourceNotFoundException notFound() { return new ResourceNotFoundException("Không tìm thấy thông tin truy cập."); }
    private LocalDateTime now() { return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC).truncatedTo(ChronoUnit.MILLIS); }
    private Instant utc(LocalDateTime value) { return value == null ? null : value.toInstant(ZoneOffset.UTC); }
}
