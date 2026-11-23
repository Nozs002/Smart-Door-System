package com.smartdoor.backend.door;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartdoor.backend.device.dto.LifecycleRequest.Action;
import com.smartdoor.backend.device.entity.Device;
import com.smartdoor.backend.device.repository.DeviceLifecycleEventRepository;
import com.smartdoor.backend.device.repository.DeviceRepository;
import com.smartdoor.backend.device.service.DeviceLifecycleService;
import com.smartdoor.backend.device.service.DeviceReadinessPolicy;
import com.smartdoor.backend.door.entity.Door;
import com.smartdoor.backend.door.repository.DoorRepository;
import com.smartdoor.backend.door.service.DoorProvisioningService;
import com.smartdoor.backend.door.service.DoorService;
import com.smartdoor.backend.identity.entity.Account;
import com.smartdoor.backend.identity.repository.AccountRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DeviceLifecyclePersistenceTests {
	@Autowired DoorProvisioningService provisioning;
	@Autowired DoorService doorService;
	@Autowired DeviceLifecycleService lifecycle;
	@Autowired AccountRepository accounts;
	@Autowired DoorRepository doors;
	@Autowired DeviceRepository devices;
	@Autowired DeviceLifecycleEventRepository events;

	@Test
	void twoReadyRolesActivateDoorAndReplacementPreservesHistory() {
		String suffix = UUID.randomUUID().toString();
		var result = provisioning.provision("Lifecycle test", null, "L1-" + suffix, "L2-" + suffix);
		Account account = new Account();
		account.setUsername(suffix);
		account.setDisplayName("Lifecycle owner");
		account.setPasswordHash("test-placeholder");
		account = accounts.saveAndFlush(account);
		doorService.claimDoor(account.getId(), result.activationCode());
		var nodes = devices.findAllByDoorId(result.doorId());
		Device first = nodes.stream().filter(n -> n.getNodeType() == Device.NodeType.NODE_1).findFirst().orElseThrow();
		Device second = nodes.stream().filter(n -> n.getNodeType() == Device.NodeType.NODE_2).findFirst().orElseThrow();
		lifecycle.changeLifecycle(account.getId(), result.doorId(), first.getId(), Action.ACTIVATE, null);
		assertThat(doors.findById(result.doorId()).orElseThrow().getStatus()).isEqualTo(Door.Status.SETUP_PENDING);
		lifecycle.changeLifecycle(account.getId(), result.doorId(), second.getId(), Action.ACTIVATE, null);
		assertThat(doors.findById(result.doorId()).orElseThrow().getStatus()).isEqualTo(Door.Status.ACTIVE);
		var replacement = lifecycle.registerReplacement(result.doorId(), first.getId(), "LR-" + suffix, "Hardware failure");
		lifecycle.replaceDevice(account.getId(), result.doorId(), first.getId(), replacement.id(), "Hardware failure");
		assertThat(devices.findById(first.getId()).orElseThrow().getLifecycleStatus()).isEqualTo(Device.LifecycleStatus.RETIRED);
		assertThat(devices.findById(replacement.id()).orElseThrow().getLifecycleStatus()).isEqualTo(Device.LifecycleStatus.ACTIVE);
		assertThat(events.findAll().stream().filter(e -> e.getDeviceId().equals(first.getId())
				&& replacement.id().equals(e.getReplacementDeviceId()))).hasSize(1);
		lifecycle.changeLifecycle(account.getId(), result.doorId(), replacement.id(), Action.DISABLE, "Maintenance");
		assertThat(doors.findById(result.doorId()).orElseThrow().getStatus()).isEqualTo(Door.Status.SETUP_PENDING);
	}

	/** Bằng chứng readiness chỉ giả trong test, không bao giờ bật bằng profile chạy thật. */
	@TestConfiguration
	static class VerifiedReadinessFixture {
		@Bean
		@Primary
		DeviceReadinessPolicy verifiedForTest() {
			return new DeviceReadinessPolicy() {
				@Override public boolean canActivate(Device device) { return true; }
				@Override public boolean canReplace(Device previous, Device replacement) { return true; }
			};
		}
	}
}
