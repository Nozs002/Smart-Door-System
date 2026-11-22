package com.smartdoor.backend.onboarding;

import static org.assertj.core.api.Assertions.*;
import com.smartdoor.backend.device.entity.Device;
import com.smartdoor.backend.device.dto.LifecycleRequest.Action;
import com.smartdoor.backend.device.repository.DeviceRepository;
import com.smartdoor.backend.device.service.DeviceLifecycleService;
import com.smartdoor.backend.door.repository.DoorRepository;
import com.smartdoor.backend.door.service.DoorService;
import com.smartdoor.backend.identity.entity.Account;
import com.smartdoor.backend.identity.repository.AccountRepository;
import com.smartdoor.backend.infrastructure.mqtt.DeviceTransportCredentialRepository;
import com.smartdoor.backend.infrastructure.mqtt.DeviceTransportCredentialService;
import com.smartdoor.backend.credentials.entity.SystemPin;
import com.smartdoor.backend.credentials.repository.SystemPinRepository;
import com.smartdoor.backend.settings.repository.DoorConfigRepository;
import com.smartdoor.backend.shared.error.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

@SpringBootTest(properties={
    "app.settings-mqtt.device-master-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
    "app.onboarding.mqtt-public-host=192.0.2.10",
    "app.onboarding.bootstrap-url=http://192.0.2.10:8080/api/v1/device-enrollment"
})
@ActiveProfiles("test") @Transactional
class ReplacementTests {
    @Autowired ReplacementService service;
    @Autowired ReplacementRepository requests;
    @Autowired OnboardingService onboarding;
    @Autowired InventoryRepository inventory;
    @Autowired AccountRepository accounts;
    @Autowired DeviceRepository devices;
    @Autowired DeviceLifecycleService lifecycle;
    @Autowired DoorService doorService;
    @Autowired DoorRepository doors;
    @Autowired DoorConfigRepository configs;
    @Autowired SystemPinRepository pins;
    @Autowired DeviceTransportCredentialService credentials;
    @Autowired DeviceTransportCredentialRepository keys;

    private Account account(Account.Role role) {
        var account=new Account(); account.setUsername("repair-"+UUID.randomUUID()); account.setDisplayName("Test");
        account.setPasswordHash("test-only-hash"); account.setRole(role); return accounts.saveAndFlush(account);
    }
    private record Fixture(Long owner,Long tech,Long door,Long old) {}
    private Fixture fixture() {
        var owner=account(Account.Role.CUSTOMER); var tech=account(Account.Role.TECHNICIAN);
        var first=onboarding.register(tech.getId(),Device.NodeType.NODE_1);
        var second=onboarding.register(tech.getId(),Device.NodeType.NODE_2);
        var kit=onboarding.createKit(tech.getId(),UUID.randomUUID().toString(),"Existing Door",null,first.getUnitCode(),second.getUnitCode());
        Long door=(Long)kit.get("doorId"); doorService.claimDoor(owner.getId(),(String)kit.get("activationCode"));
        var pin=new SystemPin(); pin.setDoorId(door); pin.setPinHash("test-pin-verifier"); pins.saveAndFlush(pin);
        for (var node:devices.findAllByDoorId(door)) {
            ready(node.getId()); lifecycle.changeLifecycle(owner.getId(),door,node.getId(),Action.ACTIVATE,null);
        }
        return new Fixture(owner.getId(),tech.getId(),door,devices.findByDeviceCode(first.getUnitCode()).orElseThrow().getId());
    }
    private void ready(Long id) {
        var device=devices.findById(id).orElseThrow(); var config=configs.findById(device.getDoorId()).orElseThrow();
        device.setAppliedConfigVersion(config.getVersion()); device.setAppliedPinVersion(config.getCredentialsVersion()); devices.saveAndFlush(device);
        var key=keys.findById(id).orElseThrow(); key.setVerifiedAt(LocalDateTime.now(ZoneOffset.UTC)); keys.saveAndFlush(key);
    }
    private Map<String,Object> request(Fixture f) { return service.create(f.owner,f.door,f.old,UUID.randomUUID().toString(),"Node bị hỏng"); }
    private Map<String,Object> bind(Fixture f,Map<String,Object> request) {
        var unit=onboarding.register(f.tech,Device.NodeType.NODE_1);
        return service.bind(f.tech,(String)request.get("authorizationCode"),unit.getUnitCode());
    }
    private void brokerProof(Fixture f) {
        var key=keys.findById(f.old).orElseThrow();
        assertThat(credentials.importBrokerProof(f.old,key.getCredentialId(),key.getRevokedAt().toString())).isTrue();
    }

    @Test void cutoverWaitsForRealBrokerProofAndPreservesDoorOwnerAndHistory() {
        var f=fixture(); var request=request(f); var bound=bind(f,request); Long replacement=(Long)bound.get("newDeviceId");
        assertThat(bound).doesNotContainKeys("authorizationCode","newNodeSetup","password","signingKey");
        assertThat(devices.findById(f.old).orElseThrow().getLifecycleStatus()).isEqualTo(Device.LifecycleStatus.ACTIVE);
        assertThatThrownBy(() -> service.complete(f.owner,f.door,(String)request.get("id"))).isInstanceOf(ConflictException.class);
        ready(replacement);
        var pending=service.complete(f.owner,f.door,(String)request.get("id"));
        assertThat(pending.get("status")).isEqualTo("REVOKING");
        assertThat(devices.findById(f.old).orElseThrow().getLifecycleStatus()).isEqualTo(Device.LifecycleStatus.DISABLED);
        assertThat(devices.findById(replacement).orElseThrow().getLifecycleStatus()).isEqualTo(Device.LifecycleStatus.PROVISIONED);
        assertThat(keys.findById(f.old).orElseThrow().getBrokerRevokedAt()).isNull();
        brokerProof(f);
        assertThat(service.complete(f.owner,f.door,(String)request.get("id")).get("status")).isEqualTo("COMPLETED");
        assertThat(service.complete(f.owner,f.door,(String)request.get("id")).get("status")).isEqualTo("COMPLETED");
        assertThat(doors.findById(f.door).orElseThrow().getOwnerAccountId()).isEqualTo(f.owner);
        assertThat(doors.findById(f.door).orElseThrow().getStatus().name()).isEqualTo("ACTIVE");
        assertThat(devices.findById(f.old).orElseThrow().getLifecycleStatus()).isEqualTo(Device.LifecycleStatus.RETIRED);
        assertThat(devices.findAllByDoorId(f.door)).hasSize(3);
        assertThat(inventory.findAllByDeviceIdIn(List.of(f.old))).hasSize(1);
    }

    @Test void retiredSourceCanBeFilledWithoutResurrectingItsIdentity() {
        var f=fixture(); lifecycle.changeLifecycle(f.owner,f.door,f.old,Action.RETIRE,"Hỏng");
        var retiredAt=devices.findById(f.old).orElseThrow().getRetiredAt();
        assertThat(keys.findById(f.old).orElseThrow().getRevokedAt()).isNotNull();
        var request=request(f); var bound=bind(f,request); ready((Long)bound.get("newDeviceId"));
        assertThatThrownBy(() -> lifecycle.changeLifecycle(f.owner,f.door,(Long)bound.get("newDeviceId"),Action.ACTIVATE,null))
            .isInstanceOf(ConflictException.class);
        brokerProof(f);
        assertThat(service.complete(f.owner,f.door,(String)request.get("id")).get("status")).isEqualTo("COMPLETED");
        assertThat(devices.findById(f.old).orElseThrow().getRetiredAt()).isEqualTo(retiredAt);
        assertThatThrownBy(() -> credentials.current(f.old)).isInstanceOf(ConflictException.class);
    }

    @Test void authorizationCannotCrossOwnersRolesInventoryOrReplayToAnotherBoard() {
        var f=fixture(); var other=account(Account.Role.CUSTOMER); var foreignTech=account(Account.Role.TECHNICIAN);
        assertThatThrownBy(() -> service.create(other.getId(),f.door,f.old,UUID.randomUUID().toString(),"Wrong owner")).isInstanceOf(ResourceNotFoundException.class);
        var request=request(f); String code=(String)request.get("authorizationCode");
        var wrongRole=onboarding.register(f.tech,Device.NodeType.NODE_2);
        assertThatThrownBy(() -> service.bind(f.tech,code,wrongRole.getUnitCode())).isInstanceOf(ConflictException.class);
        var foreign=onboarding.register(foreignTech.getId(),Device.NodeType.NODE_1);
        assertThatThrownBy(() -> service.bind(f.tech,code,foreign.getUnitCode())).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.bind(f.owner,code,foreign.getUnitCode())).isInstanceOf(AccessDeniedException.class);
        var unit=onboarding.register(f.tech,Device.NodeType.NODE_1);
        var bound=service.bind(f.tech,code.toLowerCase(Locale.ROOT),unit.getUnitCode());
        assertThat(service.bind(f.tech,code,unit.getUnitCode()).get("newDeviceId")).isEqualTo(bound.get("newDeviceId"));
        assertThatThrownBy(() -> service.bind(f.tech,code,foreign.getUnitCode())).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> service.complete(other.getId(),f.door,(String)request.get("id"))).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test void expiredAndCancelledGrantsCannotBindAndCancellationKeepsOldNodeUsable() {
        var f=fixture(); var first=request(f); var stored=requests.findById((String)first.get("id")).orElseThrow();
        stored.setCreatedAt(LocalDateTime.now().minusDays(3)); stored.setExpiresAt(LocalDateTime.now().minusDays(2)); requests.saveAndFlush(stored);
        var unit=onboarding.register(f.tech,Device.NodeType.NODE_1);
        assertThatThrownBy(() -> service.bind(f.tech,(String)first.get("authorizationCode"),unit.getUnitCode())).isInstanceOf(ConflictException.class);
        var second=request(f); var bound=service.bind(f.tech,(String)second.get("authorizationCode"),unit.getUnitCode());
        assertThat(service.cancel(f.owner,f.door,(String)second.get("id")).get("status")).isEqualTo("CANCELLED");
        assertThat(devices.findById(f.old).orElseThrow().getLifecycleStatus()).isEqualTo(Device.LifecycleStatus.ACTIVE);
        assertThat(devices.findById((Long)bound.get("newDeviceId")).orElseThrow().getLifecycleStatus()).isEqualTo(Device.LifecycleStatus.RETIRED);
        assertThatThrownBy(() -> service.bind(f.tech,(String)second.get("authorizationCode"),unit.getUnitCode())).isInstanceOf(ConflictException.class);
    }

    @Test void onlyMatchingRevocationEpochCanConfirmBrokerRemoval() {
        var f=fixture(); credentials.revoke(f.old); var key=keys.findById(f.old).orElseThrow();
        assertThat(credentials.importBrokerProof(f.old,UUID.randomUUID().toString(),key.getRevokedAt().toString())).isFalse();
        assertThat(credentials.importBrokerProof(f.old,key.getCredentialId(),key.getRevokedAt().minusSeconds(1).toString())).isFalse();
        assertThat(credentials.brokerRevoked(f.old)).isFalse(); brokerProof(f); assertThat(credentials.brokerRevoked(f.old)).isTrue();
    }

    @Test @Transactional(propagation=Propagation.NOT_SUPPORTED)
    void concurrentRetryBindsExactlyOneBoardAndReturnsTheSameResult() throws Exception {
        var f=fixture(); var request=request(f); var unit=onboarding.register(f.tech,Device.NodeType.NODE_1);
        var start=new CountDownLatch(1);
        try (var executor=Executors.newFixedThreadPool(2)) {
            Callable<Map<String,Object>> bind=() -> { start.await(); return service.bind(f.tech,(String)request.get("authorizationCode"),unit.getUnitCode()); };
            var first=executor.submit(bind); var second=executor.submit(bind); start.countDown();
            assertThat(first.get(15,TimeUnit.SECONDS).get("newDeviceId")).isEqualTo(second.get(15,TimeUnit.SECONDS).get("newDeviceId"));
        }
        assertThat(devices.findAllByDoorId(f.door)).hasSize(3);
    }
}
