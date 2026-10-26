package com.smartdoor.backend.door.service;

import com.smartdoor.backend.device.entity.Device;
import com.smartdoor.backend.device.repository.DeviceRepository;
import com.smartdoor.backend.door.dto.DoorView;
import com.smartdoor.backend.door.dto.UpdateDoorRequest;
import com.smartdoor.backend.door.entity.Door;
import com.smartdoor.backend.door.entity.DoorActivationCode;
import com.smartdoor.backend.door.repository.DoorActivationCodeRepository;
import com.smartdoor.backend.door.repository.DoorRepository;
import com.smartdoor.backend.identity.entity.Account;
import com.smartdoor.backend.identity.repository.AccountRepository;
import com.smartdoor.backend.shared.api.PageResponse;
import com.smartdoor.backend.shared.error.AuthenticationRequiredException;
import com.smartdoor.backend.shared.error.BadRequestException;
import com.smartdoor.backend.shared.error.ConflictException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class DoorService {
	private final DoorRepository doors;
	private final DoorActivationCodeRepository codes;
	private final DeviceRepository devices;
	private final AccountRepository accounts;
	private final DoorOwnershipService ownership;
	private final ActivationCodes activationCodes;
	private final Clock clock;

	public DoorService(DoorRepository doors, DoorActivationCodeRepository codes, DeviceRepository devices,
			AccountRepository accounts, DoorOwnershipService ownership, ActivationCodes activationCodes, Clock clock) {
		this.doors = doors; this.codes = codes; this.devices = devices; this.accounts = accounts;
		this.ownership = ownership; this.activationCodes = activationCodes; this.clock = clock;
	}

	public PageResponse<DoorView> listDoors(Long accountId, int page, int size) {
		requireActiveAccount(accountId);
		return PageResponse.from(doors.findAllByOwnerAccountId(accountId, PageResponse.pageable(page, size))
				.map(DoorView::from));
	}

	public DoorView getDoor(Long accountId, Long doorId) {
		return DoorView.from(ownership.getOwnedDoor(accountId, doorId));
	}

	@Transactional
	public DoorView updateDoor(Long accountId, Long doorId, UpdateDoorRequest request) {
		Door door = ownership.requireOwnerForUpdate(accountId, doorId);
		if (request.name() == null || request.name().isBlank() || request.name().length() > 100
				|| (request.location() != null && request.location().length() > 255)) {
			throw new BadRequestException("Tên cửa bắt buộc, tối đa 100 ký tự; vị trí tối đa 255 ký tự.");
		}
		door.setName(request.name().strip());
		door.setLocation(request.location() == null || request.location().isBlank() ? null : request.location().strip());
		return DoorView.from(doors.saveAndFlush(door));
	}

	@Transactional
	public DoorView claimDoor(Long accountId, String rawCode) {
		requireActiveAccount(accountId);
		String hash = activationCodes.hash(rawCode);
		Long candidateDoorId = codes.findDoorIdByCodeHash(hash).orElseThrow(this::invalidCode);
		// Tất cả workflow khóa Door trước rồi mới khóa mã/Node để tránh đảo thứ tự khóa.
		Door door = doors.findByIdForUpdate(candidateDoorId).orElseThrow(this::invalidCode);
		DoorActivationCode code = codes.findByHashAndDoorIdForUpdate(hash, door.getId()).orElseThrow(this::invalidCode);
		if (code.getConsumedAt() != null) {
			if (accountId.equals(code.getConsumedByAccountId()) && accountId.equals(door.getOwnerAccountId())) {
				return DoorView.from(door);
			}
			throw invalidCode();
		}
		LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
		if (code.getRevokedAt() != null || !code.getExpiresAt().isAfter(now)
				|| door.getOwnerAccountId() != null || door.getStatus() != Door.Status.UNCLAIMED) throw invalidCode();
		List<Device> nodes = devices.findAllByDoorId(door.getId());
		if (nodes.size() != 2 || nodes.stream().anyMatch(node -> node.getLifecycleStatus() != Device.LifecycleStatus.PROVISIONED)
				|| nodes.stream().map(Device::getNodeType).distinct().count() != 2) {
			throw new ConflictException("Bộ cửa chưa có đúng hai Node provision hợp lệ.");
		}
		door.setOwnerAccountId(accountId);
		door.setStatus(Door.Status.SETUP_PENDING);
		code.setConsumedAt(now);
		code.setConsumedByAccountId(accountId);
		codes.save(code);
		return DoorView.from(doors.saveAndFlush(door));
	}

	private void requireActiveAccount(Long accountId) {
		if (accountId == null || !accounts.findById(accountId).map(account -> account.getStatus() == Account.Status.ACTIVE).orElse(false)) {
			throw new AuthenticationRequiredException();
		}
	}

	private BadRequestException invalidCode() {
		return new BadRequestException("Mã kích hoạt không hợp lệ hoặc không thể sử dụng.");
	}
}
