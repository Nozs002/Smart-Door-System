package com.smartdoor.backend.door;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.smartdoor.backend.device.dto.LifecycleRequest.Action;
import com.smartdoor.backend.device.entity.Device;
import com.smartdoor.backend.device.repository.DeviceLifecycleEventRepository;
import com.smartdoor.backend.device.repository.DeviceRepository;
import com.smartdoor.backend.device.service.DeviceLifecycleService;
import com.smartdoor.backend.device.service.DeviceReadinessPolicy;
import com.smartdoor.backend.door.repository.DoorRepository;
import com.smartdoor.backend.door.service.DoorProvisioningService;
import com.smartdoor.backend.door.service.DoorService;
import com.smartdoor.backend.identity.entity.Account;
import com.smartdoor.backend.identity.repository.AccountRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** Không bọc test trong transaction: kiểm tra rollback thật ở ranh giới service proxy. */
@SpringBootTest
@ActiveProfiles("test")
class WorkflowRollbackTests {
	@Autowired DoorProvisioningService provisioning;
	@Autowired DoorService doorService;
	@Autowired DeviceLifecycleService lifecycle;
	@Autowired DoorRepository doors;
	@Autowired DeviceRepository devices;
	@Autowired AccountRepository accounts;
	@MockitoBean DeviceLifecycleEventRepository events;
	@MockitoBean DeviceReadinessPolicy readiness;

	@Test
	void provisionRollsBackDoorAndNodeWhenAuditCannotBeSaved() {
		long doorCount = doors.count();
		long deviceCount = devices.count();
		when(events.save(any())).thenThrow(new IllegalStateException("Simulated audit failure"));
		String suffix = UUID.randomUUID().toString();
		assertThatThrownBy(() -> provisioning.provision("Rollback", null, "R1-" + suffix, "R2-" + suffix))
				.isInstanceOf(IllegalStateException.class);
		assertThat(doors.count()).isEqualTo(doorCount);
		assertThat(devices.count()).isEqualTo(deviceCount);
	}

	@Test
	void lifecycleStateRollsBackWhenAuditCannotBeSaved() {
		String suffix = UUID.randomUUID().toString();
		var result = provisioning.provision("Rollback lifecycle", null, "A1-" + suffix, "A2-" + suffix);
		Account account = new Account();
		account.setUsername(suffix);
		account.setDisplayName("Rollback owner");
		account.setPasswordHash("test-placeholder");
		account = accounts.saveAndFlush(account);
		doorService.claimDoor(account.getId(), result.activationCode());
		Long ownerId = account.getId();
		Device device = devices.findAllByDoorId(result.doorId()).getFirst();
		when(readiness.canActivate(any())).thenReturn(true);
		when(events.save(any())).thenThrow(new IllegalStateException("Simulated audit failure"));
		assertThatThrownBy(() -> lifecycle.changeLifecycle(ownerId, result.doorId(), device.getId(), Action.ACTIVATE, null))
				.isInstanceOf(IllegalStateException.class);
		Device persisted = devices.findById(device.getId()).orElseThrow();
		assertThat(persisted.getLifecycleStatus()).isEqualTo(Device.LifecycleStatus.PROVISIONED);
		assertThat(persisted.getActivatedAt()).isNull();
	}
}
