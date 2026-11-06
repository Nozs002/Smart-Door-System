package com.smartdoor.backend.settings;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.smartdoor.backend.alert.entity.Alert;
import com.smartdoor.backend.alert.notification.NotificationRepository;
import com.smartdoor.backend.alert.notification.NotificationService;
import com.smartdoor.backend.alert.repository.AlertRepository;
import com.smartdoor.backend.alert.service.AlertService;
import com.smartdoor.backend.alert.service.HardwareAlertEvent;
import com.smartdoor.backend.alert.service.HardwareAlertService;
import com.smartdoor.backend.device.entity.Device;
import com.smartdoor.backend.device.repository.DeviceRepository;
import com.smartdoor.backend.device.service.DeviceHeartbeatService.AuthenticatedDeviceIdentity;
import com.smartdoor.backend.door.entity.Door;
import com.smartdoor.backend.door.repository.DoorRepository;
import com.smartdoor.backend.identity.entity.Account;
import com.smartdoor.backend.identity.dto.LoginRequest;
import com.smartdoor.backend.identity.repository.AccountRepository;
import com.smartdoor.backend.identity.service.LoginService;
import com.smartdoor.backend.infrastructure.outbox.OutboxRepository;
import com.smartdoor.backend.infrastructure.outbox.OutboxService;
import com.smartdoor.backend.settings.dto.UpdateDoorConfigRequest;
import com.smartdoor.backend.settings.entity.DoorConfig;
import com.smartdoor.backend.settings.repository.DoorConfigRepository;
import com.smartdoor.backend.settings.service.DeviceSyncAcknowledgementService;
import com.smartdoor.backend.settings.service.DoorSettingsService;
import com.smartdoor.backend.shared.error.BadRequestException;
import com.smartdoor.backend.shared.error.ResourceNotFoundException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

/** Real service proxies and database locks, without a wrapping test transaction. */
@SpringBootTest(properties = {"app.outbox.enabled=false", "app.mqtt.enabled=false",
		"spring.datasource.url=jdbc:h2:mem:settings_alerts_tx;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000"})
@ActiveProfiles("test")
class SettingsAlertTransactionTests {
	@Autowired TransactionTemplate tx;
	@Autowired DoorSettingsService settings;
	@Autowired HardwareAlertService hardware;
	@Autowired AlertService alertService;
	@Autowired NotificationService inbox;
	@Autowired DeviceSyncAcknowledgementService acknowledgements;
	@Autowired OutboxService outbox;
	@Autowired AccountRepository accounts;
	@Autowired DoorRepository doors;
	@Autowired DeviceRepository devices;
	@Autowired DoorConfigRepository configs;
	@Autowired AlertRepository alerts;
	@Autowired NotificationRepository notifications;
	@Autowired OutboxRepository deliveries;
	@Autowired WebApplicationContext web;
	@Autowired LoginService logins;
	@Autowired PasswordEncoder passwords;

	@Test void desiredStateAndOutboundMessagesRollBackTogether() {
		Fixture fixture = fixture();
		assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
			settings.update(fixture.accountId, fixture.doorId,
					new UpdateDoorConfigRequest(5, 120, new BigDecimal("60.00"), 30));
			assertThat(outbox.status(fixture.accountId, fixture.doorId, 0, 20).totalElements()).isEqualTo(2);
			throw new IllegalStateException("Abort after enqueue");
		})).isInstanceOf(IllegalStateException.class);
		assertThat(configs.findById(fixture.doorId).orElseThrow().getVersion()).isEqualTo(1);
		assertThat(outbox.status(fixture.accountId, fixture.doorId, 0, 20).totalElements()).isZero();
		settings.update(fixture.accountId, fixture.doorId,
				new UpdateDoorConfigRequest(5, 120, new BigDecimal("60.00"), 30));
		assertThat(configs.findById(fixture.doorId).orElseThrow().getVersion()).isEqualTo(2);
		assertThat(outbox.status(fixture.accountId, fixture.doorId, 0, 20).totalElements()).isEqualTo(2);
	}

	@Test void simultaneousDuplicateAlertsPersistOneAlertAndOneOwnerNotification() throws Exception {
		Fixture fixture = fixture();
		HardwareAlertEvent event = event(UUID.randomUUID().toString());
		CountDownLatch start = new CountDownLatch(1);
		Callable<Long> receive = () -> {
			if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Alert barrier timed out");
			return hardware.receive(fixture.nodeTwo(), event).id();
		};
		try (var executor = Executors.newFixedThreadPool(2)) {
			var first = executor.submit(receive); var second = executor.submit(receive); start.countDown();
			Long id = first.get(15, TimeUnit.SECONDS);
			assertThat(second.get(15, TimeUnit.SECONDS)).isEqualTo(id);
			assertThat(alerts.findByEventId(event.eventId())).get().extracting(Alert::getId).isEqualTo(id);
			assertThat(inbox.list(fixture.accountId, false, 0, 20).totalElements()).isEqualTo(1);
			assertThat(notifications.findByAlertIdAndAccountId(id, fixture.accountId)).isPresent();
		}
	}

	@Test void alertAndNotificationRollBackTogetherThenRetrySucceeds() {
		Fixture fixture = fixture(); HardwareAlertEvent event = event(UUID.randomUUID().toString());
		assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
			hardware.receive(fixture.nodeTwo(), event);
			throw new IllegalStateException("Abort after inbox write");
		})).isInstanceOf(IllegalStateException.class);
		assertThat(alerts.findByEventId(event.eventId())).isEmpty();
		assertThat(inbox.list(fixture.accountId, false, 0, 20).totalElements()).isZero();
		hardware.receive(fixture.nodeTwo(), event);
		assertThat(inbox.list(fixture.accountId, false, 0, 20).totalElements()).isEqualTo(1);
	}

	@Test void concurrentOutboxWorkersNeverClaimTheSameUnexpiredMessage() throws Exception {
		Fixture fixture = fixture();
		tx.executeWithoutResult(status -> {
			outbox.enqueue(OutboxService.CREDENTIALS, fixture.doorId, fixture.nodeOneCode, "{\"version\":1}");
			outbox.enqueue(OutboxService.CREDENTIALS, fixture.doorId, fixture.nodeOneCode, "{\"version\":2}");
		});
		CountDownLatch start = new CountDownLatch(1);
		Callable<OutboxService.Claim> claim = () -> {
			if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Outbox barrier timed out");
			var claimed = outbox.claim(List.of(OutboxService.CREDENTIALS), 1);
			return claimed.isEmpty() ? null : claimed.getFirst();
		};
		try (var executor = Executors.newFixedThreadPool(2)) {
			var first = executor.submit(claim); var second = executor.submit(claim); start.countDown();
			var a = first.get(15, TimeUnit.SECONDS); var b = second.get(15, TimeUnit.SECONDS);
			assertThat(a).isNotNull(); assertThat(b).isNotNull();
			assertThat(a.delivery().id()).isNotEqualTo(b.delivery().id());
			assertThat(a.delivery().doorId()).isEqualTo(fixture.doorId);
			assertThat(b.delivery().doorId()).isEqualTo(fixture.doorId);
			outbox.delivered(a); outbox.delivered(b);
		}
	}

	@Test void simultaneousAcknowledgeAndResolveCannotReopenResolvedAlert() throws Exception {
		Fixture fixture = fixture();
		Long alertId = hardware.receive(fixture.nodeTwo(), event(UUID.randomUUID().toString())).id();
		CountDownLatch start = new CountDownLatch(1);
		try (var executor = Executors.newFixedThreadPool(2)) {
			var resolve = executor.submit(statusChange(start, fixture, alertId, Alert.Status.RESOLVED));
			var acknowledge = executor.submit(statusChange(start, fixture, alertId, Alert.Status.ACKNOWLEDGED));
			start.countDown();
			assertThat(resolve.get(15, TimeUnit.SECONDS)).isTrue();
			acknowledge.get(15, TimeUnit.SECONDS);
		}
		var resolved = alerts.findById(alertId).orElseThrow();
		assertThat(resolved.getStatus()).isEqualTo(Alert.Status.RESOLVED);
		assertThat(resolved.getResolvedAt()).isNotNull();
		assertThat(alertService.changeStatus(fixture.accountId, alertId, Alert.Status.RESOLVED).resolvedAt())
				.isEqualTo(resolved.getResolvedAt());
	}

	@Test void provisionedNodeCanAcknowledgeSetupWithoutBeingActivatedAndInboxIsOwnerScoped() {
		Fixture fixture = fixture();
		tx.executeWithoutResult(status -> {
			Device node = devices.findById(fixture.nodeOneId).orElseThrow();
			node.setLifecycleStatus(Device.LifecycleStatus.PROVISIONED); node.setActivatedAt(null);
		});
		acknowledgements.acknowledgeConfiguration(fixture.nodeOne(), 1L);
		acknowledgements.acknowledgeCredentials(fixture.nodeOne(), 1L);
		Device node = devices.findById(fixture.nodeOneId).orElseThrow();
		assertThat(node.getAppliedConfigVersion()).isEqualTo(1); assertThat(node.getAppliedPinVersion()).isEqualTo(1);
		assertThat(node.getLifecycleStatus()).isEqualTo(Device.LifecycleStatus.PROVISIONED);
		hardware.receive(fixture.nodeTwo(), event(UUID.randomUUID().toString()));
		Long notificationId = inbox.list(fixture.accountId, true, 0, 20).items().getFirst().id();
		assertThatThrownBy(() -> inbox.markRead(fixture.accountId + 10000, notificationId))
				.isInstanceOf(ResourceNotFoundException.class);
		inbox.markRead(fixture.accountId, notificationId);
		assertThat(inbox.list(fixture.accountId, true, 0, 20).totalElements()).isZero();
		assertThat(inbox.list(fixture.accountId, false, 0, 20).totalElements()).isEqualTo(1);
	}

	@Test void notificationAndDeliveryApisRequireJwtAndHideOtherOwnersResources() throws Exception {
		Fixture owner = fixture(); Fixture other = fixture();
		hardware.receive(other.nodeTwo(), event(UUID.randomUUID().toString()));
		Long hiddenNotification = inbox.list(other.accountId, false, 0, 20).items().getFirst().id();
		Account account = accounts.findById(owner.accountId).orElseThrow();
		String token = logins.login(new LoginRequest(account.getUsername(), "settings-password")).accessToken();
		var mvc = MockMvcBuilders.webAppContextSetup(web).apply(springSecurity()).build();
		mvc.perform(get("/api/v1/notifications")).andExpect(status().isUnauthorized());
		mvc.perform(get("/api/v1/doors/{id}/delivery-status", owner.doorId)).andExpect(status().isUnauthorized());
		mvc.perform(get("/api/v1/notifications").header("Authorization", "Bearer " + token))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(0));
		mvc.perform(post("/api/v1/notifications/{id}/read", hiddenNotification).header("Authorization", "Bearer " + token))
				.andExpect(status().isNotFound());
		mvc.perform(post("/api/v1/doors/{id}/sync/retry", other.doorId).header("Authorization", "Bearer " + token))
				.andExpect(status().isNotFound());
		mvc.perform(get("/api/v1/doors/{id}/delivery-status", other.doorId).header("Authorization", "Bearer " + token))
				.andExpect(status().isNotFound());
	}

	private Callable<Boolean> statusChange(CountDownLatch start, Fixture fixture, Long id, Alert.Status target) {
		return () -> {
			if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Status barrier timed out");
			try { alertService.changeStatus(fixture.accountId, id, target); return true; }
			catch (BadRequestException terminalStatus) {
				if (target != Alert.Status.ACKNOWLEDGED) throw terminalStatus;
				return false;
			}
		};
	}
	private HardwareAlertEvent event(String eventId) {
		return new HardwareAlertEvent(eventId, Alert.Type.HIGH_TEMPERATURE, new BigDecimal("55.5"),
				LocalDateTime.of(2026, 10, 28, 1, 2, 3, 456_000_000));
	}
	private Fixture fixture() {
		return tx.execute(status -> {
			String suffix = UUID.randomUUID().toString();
			Account account = new Account(); account.setUsername(suffix); account.setDisplayName("Settings owner");
			account.setPasswordHash(passwords.encode("settings-password")); account = accounts.saveAndFlush(account);
			Door door = new Door(); door.setOwnerAccountId(account.getId()); door.setName("Settings " + suffix);
			door.setStatus(Door.Status.ACTIVE); door = doors.saveAndFlush(door);
			DoorConfig config = new DoorConfig(); config.setDoorId(door.getId()); configs.saveAndFlush(config);
			Device one = node(door.getId(), "S1-" + suffix, Device.NodeType.NODE_1);
			Device two = node(door.getId(), "S2-" + suffix, Device.NodeType.NODE_2);
			return new Fixture(account.getId(), door.getId(), one.getId(), one.getDeviceCode(), two.getDeviceCode());
		});
	}
	private Device node(Long doorId, String code, Device.NodeType type) {
		Device node = new Device(); node.setDoorId(doorId); node.setDeviceCode(code); node.setNodeType(type);
		node.setLifecycleStatus(Device.LifecycleStatus.ACTIVE); node.setActivatedAt(LocalDateTime.now());
		return devices.saveAndFlush(node);
	}
	private record Fixture(Long accountId, Long doorId, Long nodeOneId, String nodeOneCode, String nodeTwoCode) {
		AuthenticatedDeviceIdentity nodeOne() { return new AuthenticatedDeviceIdentity(nodeOneCode, doorId, Device.NodeType.NODE_1); }
		AuthenticatedDeviceIdentity nodeTwo() { return new AuthenticatedDeviceIdentity(nodeTwoCode, doorId, Device.NodeType.NODE_2); }
	}
}
