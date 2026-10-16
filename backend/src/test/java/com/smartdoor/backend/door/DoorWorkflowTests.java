package com.smartdoor.backend.door;

import static org.assertj.core.api.Assertions.*;

import com.smartdoor.backend.access.repository.LockoutStatusRepository;
import com.smartdoor.backend.device.entity.Device;
import com.smartdoor.backend.device.repository.DeviceLifecycleEventRepository;
import com.smartdoor.backend.device.repository.DeviceRepository;
import com.smartdoor.backend.door.dto.UpdateDoorRequest;
import com.smartdoor.backend.door.entity.Door;
import com.smartdoor.backend.door.entity.DoorActivationCode;
import com.smartdoor.backend.door.repository.DoorActivationCodeRepository;
import com.smartdoor.backend.door.repository.DoorRepository;
import com.smartdoor.backend.door.service.ActivationCodes;
import com.smartdoor.backend.door.service.DoorOwnershipService;
import com.smartdoor.backend.door.service.DoorProvisioningService;
import com.smartdoor.backend.door.service.DoorService;
import com.smartdoor.backend.identity.entity.Account;
import com.smartdoor.backend.identity.repository.AccountRepository;
import com.smartdoor.backend.settings.repository.DoorConfigRepository;
import com.smartdoor.backend.shared.error.AuthenticationRequiredException;
import com.smartdoor.backend.shared.error.BadRequestException;
import com.smartdoor.backend.shared.error.ConflictException;
import com.smartdoor.backend.shared.error.ResourceNotFoundException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DoorWorkflowTests {
	@Autowired DoorProvisioningService provisioning;
	@Autowired DoorService service;
	@Autowired DoorOwnershipService ownership;
	@Autowired ActivationCodes activationCodes;
	@Autowired AccountRepository accounts;
	@Autowired DoorRepository doors;
	@Autowired DoorActivationCodeRepository codes;
	@Autowired DeviceRepository devices;
	@Autowired DeviceLifecycleEventRepository events;
	@Autowired DoorConfigRepository configs;
	@Autowired LockoutStatusRepository lockouts;

	@Test
	void provisionCreatesAnUnclaimedPairWithConfigurationAndNoPlaintextCode() {
		var result = provision();
		Door door = doors.findById(result.doorId()).orElseThrow();
		assertThat(door.getOwnerAccountId()).isNull();
		assertThat(door.getStatus()).isEqualTo(Door.Status.UNCLAIMED);
		assertThat(devices.findAllByDoorId(door.getId())).hasSize(2)
				.extracting(Device::getNodeType).containsExactlyInAnyOrder(Device.NodeType.NODE_1, Device.NodeType.NODE_2);
		assertThat(configs.findById(door.getId())).isPresent();
		assertThat(events.findAll().stream().filter(event -> event.getDoorId().equals(door.getId()))).hasSize(2);
		assertThat(lockouts.findAll().stream().filter(status -> status.getId().getDoorId().equals(door.getId()))).hasSize(2);
		DoorActivationCode code = codes.findByCodeHash(activationCodes.hash(result.activationCode())).orElseThrow();
		assertThat(code.getCodeHash()).hasSize(64).isNotEqualTo(result.activationCode());
		assertThat(code.getExpiresAt()).isEqualTo(code.getCreatedAt().plusDays(7));
		assertThat(result.toString()).doesNotContain(result.activationCode());
	}

	@Test
	void claimIsScopedAndSameAccountRetryDoesNotDuplicateOrActivate() {
		var result = provision();
		Account owner = account();
		Account other = account();
		var claimed = service.claimDoor(owner.getId(), result.activationCode().toLowerCase().replace("-", ""));
		assertThat(claimed.status()).isEqualTo(Door.Status.SETUP_PENDING);
		assertThat(service.claimDoor(owner.getId(), result.activationCode()).id()).isEqualTo(claimed.id());
		assertThat(service.listDoors(owner.getId(), 0, 20).items()).extracting("id").contains(claimed.id());
		assertThat(service.listDoors(other.getId(), 0, 20).items()).isEmpty();
		assertThat(devices.findAllByDoorId(claimed.id())).allMatch(node -> node.getLifecycleStatus() == Device.LifecycleStatus.PROVISIONED);
		assertThatThrownBy(() -> service.claimDoor(other.getId(), result.activationCode())).isInstanceOf(BadRequestException.class);
		assertThatThrownBy(() -> ownership.requireOwner(other.getId(), claimed.id())).isInstanceOf(ResourceNotFoundException.class);
		assertThatThrownBy(() -> service.updateDoor(other.getId(), claimed.id(), new UpdateDoorRequest("Attack", null)))
				.isInstanceOf(ResourceNotFoundException.class);
	}

	@Test
	void expiredCodeCannotClaimAndReissuedCodeRevokesPrevious() {
		var first = provision();
		Account owner = account();
		var replacement = provisioning.reissue(first.doorId());
		assertThatThrownBy(() -> service.claimDoor(owner.getId(), first.activationCode())).isInstanceOf(BadRequestException.class);
		DoorActivationCode code = codes.findByCodeHash(activationCodes.hash(replacement.activationCode())).orElseThrow();
		code.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC).minusDays(9));
		code.setExpiresAt(LocalDateTime.now(ZoneOffset.UTC).minusDays(2));
		codes.saveAndFlush(code);
		assertThatThrownBy(() -> service.claimDoor(owner.getId(), replacement.activationCode())).isInstanceOf(BadRequestException.class);
		assertThat(doors.findById(first.doorId()).orElseThrow().getOwnerAccountId()).isNull();
	}

	@Test
	void claimedDoorCannotReissueAndOwnerCanEditOnlyDisplayFields() {
		var result = provision();
		Account owner = account();
		service.claimDoor(owner.getId(), result.activationCode());
		var updated = service.updateDoor(owner.getId(), result.doorId(), new UpdateDoorRequest("  Cửa chính  ", "  Tầng 1  "));
		assertThat(updated.name()).isEqualTo("Cửa chính");
		assertThat(updated.location()).isEqualTo("Tầng 1");
		assertThat(service.updateDoor(owner.getId(), result.doorId(), new UpdateDoorRequest("Cửa", " ")).location()).isNull();
		assertThatThrownBy(() -> provisioning.reissue(result.doorId())).isInstanceOf(ConflictException.class);
	}

	@Test
	void malformedPairOrInactiveAccountCannotClaim() {
		var result = provision();
		Account owner = account();
		owner.setStatus(Account.Status.DISABLED);
		accounts.saveAndFlush(owner);
		assertThatThrownBy(() -> service.claimDoor(owner.getId(), result.activationCode())).isInstanceOf(AuthenticationRequiredException.class);
		Account active = account();
		devices.delete(devices.findAllByDoorId(result.doorId()).getFirst());
		devices.flush();
		assertThatThrownBy(() -> service.claimDoor(active.getId(), result.activationCode())).isInstanceOf(ConflictException.class);
	}

	@Test
	void rejectsDuplicateDeviceCodesBeforeCreatingDoor() {
		long count = doors.count();
		assertThatThrownBy(() -> provisioning.provision("Door", null, "SAME", "SAME"))
				.isInstanceOf(ConflictException.class);
		assertThat(doors.count()).isEqualTo(count);
	}

	private DoorProvisioningService.ProvisionResult provision() {
		String suffix = UUID.randomUUID().toString();
		return provisioning.provision("Test door", null, "N1-" + suffix, "N2-" + suffix);
	}
	private Account account() {
		Account account = new Account();
		account.setDisplayName("Door test");
		account.setUsername(UUID.randomUUID().toString());
		account.setPasswordHash("not-a-real-password-hash");
		return accounts.saveAndFlush(account);
	}
}
