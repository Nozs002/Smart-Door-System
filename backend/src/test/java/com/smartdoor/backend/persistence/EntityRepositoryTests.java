package com.smartdoor.backend.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.smartdoor.backend.access.entity.AccessLog;
import com.smartdoor.backend.access.entity.LockoutStatus;
import com.smartdoor.backend.access.entity.LockoutStatusId;
import com.smartdoor.backend.access.repository.AccessLogRepository;
import com.smartdoor.backend.access.repository.LockoutStatusRepository;
import com.smartdoor.backend.alert.entity.Alert;
import com.smartdoor.backend.alert.repository.AlertRepository;
import com.smartdoor.backend.credentials.entity.Cardholder;
import com.smartdoor.backend.credentials.entity.RfidCredential;
import com.smartdoor.backend.credentials.entity.SystemPin;
import com.smartdoor.backend.credentials.repository.CardholderRepository;
import com.smartdoor.backend.credentials.repository.RfidCredentialRepository;
import com.smartdoor.backend.credentials.repository.SystemPinRepository;
import com.smartdoor.backend.device.entity.Device;
import com.smartdoor.backend.device.entity.DeviceLifecycleEvent;
import com.smartdoor.backend.device.repository.DeviceLifecycleEventRepository;
import com.smartdoor.backend.device.repository.DeviceRepository;
import com.smartdoor.backend.door.entity.Door;
import com.smartdoor.backend.door.entity.DoorActivationCode;
import com.smartdoor.backend.door.repository.DoorActivationCodeRepository;
import com.smartdoor.backend.door.repository.DoorRepository;
import com.smartdoor.backend.identity.entity.Account;
import com.smartdoor.backend.identity.repository.AccountRepository;
import com.smartdoor.backend.settings.entity.DoorConfig;
import com.smartdoor.backend.settings.repository.DoorConfigRepository;

/**
 * Kiểm tra persistence/JPQL trên H2; không thay thế kiểm thử FK, generated column
 * và concurrency thực tế trên MySQL 8.4.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class EntityRepositoryTests {

	@Autowired private EntityManager entityManager;
	@Autowired private AccountRepository accounts;
	@Autowired private CardholderRepository cardholders;
	@Autowired private DoorRepository doors;
	@Autowired private DeviceRepository devices;
	@Autowired private DoorActivationCodeRepository codes;
	@Autowired private DeviceLifecycleEventRepository lifecycleEvents;
	@Autowired private RfidCredentialRepository rfids;
	@Autowired private SystemPinRepository pins;
	@Autowired private DoorConfigRepository configs;
	@Autowired private AccessLogRepository logs;
	@Autowired private LockoutStatusRepository lockouts;
	@Autowired private AlertRepository alerts;

	@Test
	void persistsAllTablesAndQueriesOnlyRequestedScope() {
		Account owner = account("mapping-owner");
		Account other = account("mapping-other");
		Door door = door(owner.getId(), "Mapping door");
		Door otherDoor = door(other.getId(), "Other door");

		Cardholder holder = new Cardholder();
		holder.setAccountId(owner.getId());
		holder.setDisplayName("Test holder");
		holder = cardholders.saveAndFlush(holder);

		Device device = new Device();
		device.setDoorId(door.getId());
		device.setDeviceCode("MAPPING-NODE-1");
		device.setNodeType(Device.NodeType.NODE_1);
		device.setLifecycleStatus(Device.LifecycleStatus.ACTIVE);
		device.setActivatedAt(LocalDateTime.now(ZoneOffset.UTC));
		device = devices.saveAndFlush(device);

		DoorActivationCode code = new DoorActivationCode();
		code.setDoorId(door.getId());
		code.setCodeHash("test-hash-placeholder");
		code.setExpiresAt(LocalDateTime.now(ZoneOffset.UTC).plusHours(1));
		code = codes.saveAndFlush(code);

		DeviceLifecycleEvent event = new DeviceLifecycleEvent();
		event.setEventId("MAPPING-LIFECYCLE");
		event.setDoorId(door.getId());
		event.setDeviceId(device.getId());
		event.setActorAccountId(owner.getId());
		event.setEventType(DeviceLifecycleEvent.EventType.ACTIVATED);
		event.setFromStatus(Device.LifecycleStatus.PROVISIONED);
		event.setToStatus(Device.LifecycleStatus.ACTIVE);
		lifecycleEvents.saveAndFlush(event);

		RfidCredential rfid = new RfidCredential();
		rfid.setDoorId(door.getId());
		rfid.setAccountId(owner.getId());
		rfid.setCardholderId(holder.getId());
		rfid.setUidLookupValue("MAPPING-UID");
		rfid = rfids.saveAndFlush(rfid);

		SystemPin pin = new SystemPin();
		pin.setDoorId(door.getId());
		pin.setPinHash("test-hash-placeholder");
		pins.saveAndFlush(pin);

		DoorConfig config = new DoorConfig();
		config.setDoorId(door.getId());
		configs.saveAndFlush(config);

		AccessLog log = new AccessLog();
		log.setEventId("MAPPING-ACCESS");
		log.setCorrelationId("MAPPING-ATTEMPT");
		log.setDoorId(door.getId());
		log.setSourceDeviceId(device.getId());
		log.setRfidCredentialId(rfid.getId());
		log.setMethod(AccessLog.Method.RFID);
		log.setResult(AccessLog.Result.GRANTED);
		logs.saveAndFlush(log);

		LockoutStatus lockout = new LockoutStatus();
		LockoutStatusId key = new LockoutStatusId(door.getId(), LockoutStatusId.Method.PIN);
		lockout.setId(key);
		lockout.setFailedCount(2);
		lockouts.saveAndFlush(lockout);

		Alert alert = new Alert();
		alert.setDoorId(door.getId());
		alert.setSourceDeviceId(device.getId());
		alert.setType(Alert.Type.HIGH_TEMPERATURE);
		alert.setSeverity(Alert.Severity.CRITICAL);
		alert.setObservedTemperature(new BigDecimal("55.25"));
		alerts.saveAndFlush(alert);

		entityManager.clear();
		assertThat(entityManager.getMetamodel().getEntities()).extracting(jakarta.persistence.metamodel.EntityType::getName)
				.contains("Account", "Cardholder", "Door", "Device", "DoorActivationCode",
						"DeviceLifecycleEvent", "RfidCredential", "SystemPin", "DoorConfig",
						"AccessLog", "LockoutStatus", "Alert");
		assertThat(accounts.findByUsername("mapping-owner")).isPresent();
		assertThat(doors.findByIdAndOwnerAccountId(door.getId(), other.getId())).isEmpty();
		assertThat(doors.findAllByOwnerAccountId(owner.getId(), PageRequest.of(0, 10))
				.getContent()).extracting(Door::getId).containsExactly(door.getId());
		assertThat(cardholders.findByIdAndAccountId(holder.getId(), other.getId())).isEmpty();
		assertThat(rfids.findByDoorIdAndUidLookupValue(otherDoor.getId(), "MAPPING-UID")).isEmpty();
		assertThat(devices.findByDeviceCode("MAPPING-NODE-1").orElseThrow().getLifecycleStatus())
				.isEqualTo(Device.LifecycleStatus.ACTIVE);
		assertThat(devices.findByIdAndDoorId(device.getId(), otherDoor.getId())).isEmpty();
		assertThat(pins.findById(door.getId()).orElseThrow().getVersion()).isEqualTo(1L);
		assertThat(configs.findById(door.getId()).orElseThrow().getTemperatureThreshold())
				.isEqualByComparingTo("50.00");
		assertThat(lifecycleEvents.findAllByDeviceIdAndDoorIdOrderByOccurredAtDescIdDesc(
				device.getId(), door.getId(), PageRequest.of(0, 10)).getTotalElements()).isEqualTo(1);
		assertThat(lifecycleEvents.existsByEventId("MAPPING-LIFECYCLE")).isTrue();
		assertThat(logs.existsByEventId("MAPPING-ACCESS")).isTrue();
		assertThat(logs.findAllByDoorIdAndCorrelationIdOrderByReceivedAtAscIdAsc(
				otherDoor.getId(), "MAPPING-ATTEMPT")).isEmpty();
		assertThat(logs.findAllByDoorIdOrderByReceivedAtDescIdDesc(
				door.getId(), PageRequest.of(0, 10)).getContent().getFirst().getOccurredAt()).isNull();
		assertThat(lockouts.findById(new LockoutStatusId(door.getId(), LockoutStatusId.Method.PIN))
				.orElseThrow().getFailedCount()).isEqualTo(2);
		assertThat(alerts.findAllByDoorIdAndStatusOrderByTriggeredAtDescIdDesc(
				door.getId(), Alert.Status.ACTIVE, PageRequest.of(0, 10)).getTotalElements()).isEqualTo(1);
		assertThat(doors.findByIdForUpdate(door.getId())).isPresent();
		assertThat(devices.findByIdAndDoorIdForUpdate(device.getId(), door.getId())).isPresent();
		assertThat(codes.findByIdAndDoorIdForUpdate(code.getId(), door.getId())).isPresent();
		assertThat(codes.findAllByDoorIdAndConsumedAtIsNullAndRevokedAtIsNullAndExpiresAtAfter(
				door.getId(), LocalDateTime.now(ZoneOffset.UTC))).hasSize(1);
		assertThat(lockouts.findByIdForUpdate(key)).isPresent();
	}

	@Test
	void keepsRfidAndPinCountersSeparateAndIncrementsOptimisticVersion() {
		Door door = door(account("version-owner").getId(), "Version door");
		Long previousVersion = door.getVersion();
		for (LockoutStatusId.Method method : LockoutStatusId.Method.values()) {
			LockoutStatus counter = new LockoutStatus();
			counter.setId(new LockoutStatusId(door.getId(), method));
			lockouts.saveAndFlush(counter);
		}
		LockoutStatus pin = lockouts.findById(
				new LockoutStatusId(door.getId(), LockoutStatusId.Method.PIN)).orElseThrow();
		pin.setFailedCount(3);
		door.setName("Updated name");
		entityManager.flush();
		entityManager.clear();
		assertThat(lockouts.findById(new LockoutStatusId(door.getId(), LockoutStatusId.Method.RFID))
				.orElseThrow().getFailedCount()).isZero();
		assertThat(lockouts.findById(new LockoutStatusId(door.getId(), LockoutStatusId.Method.PIN))
				.orElseThrow().getFailedCount()).isEqualTo(3);
		assertThat(doors.findById(door.getId()).orElseThrow().getVersion())
				.isEqualTo(previousVersion + 1);
	}

	private Account account(String username) {
		Account account = new Account();
		account.setDisplayName(username);
		account.setUsername(username);
		account.setPasswordHash("test-hash-placeholder");
		return accounts.saveAndFlush(account);
	}

	private Door door(Long accountId, String name) {
		Door door = new Door();
		door.setOwnerAccountId(accountId);
		door.setName(name);
		door.setStatus(Door.Status.SETUP_PENDING);
		return doors.saveAndFlush(door);
	}
}
