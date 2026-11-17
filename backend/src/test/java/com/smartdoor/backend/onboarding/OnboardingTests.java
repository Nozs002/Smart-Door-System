package com.smartdoor.backend.onboarding;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.smartdoor.backend.device.entity.Device;
import com.smartdoor.backend.device.repository.DeviceRepository;
import com.smartdoor.backend.door.service.DoorService;
import com.smartdoor.backend.door.repository.DoorRepository;
import com.smartdoor.backend.identity.entity.Account;
import com.smartdoor.backend.identity.repository.AccountRepository;
import com.smartdoor.backend.identity.service.LoginService;
import com.smartdoor.backend.identity.dto.LoginRequest;
import com.smartdoor.backend.infrastructure.mqtt.DeviceTransportCredentialService;
import com.smartdoor.backend.infrastructure.mqtt.DeviceTransportCredentialRepository;
import com.smartdoor.backend.settings.repository.DoorConfigRepository;
import com.smartdoor.backend.credentials.entity.SystemPin;
import com.smartdoor.backend.credentials.repository.SystemPinRepository;
import com.smartdoor.backend.shared.error.*;
import java.util.*;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties={
    "app.settings-mqtt.device-master-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
    "app.onboarding.mqtt-public-host=192.0.2.10",
    "app.onboarding.bootstrap-url=http://192.0.2.10:8080/api/v1/device-enrollment"
})
@ActiveProfiles("test") @Transactional
class OnboardingTests {
    @Autowired OnboardingService service;
    @Autowired AccountRepository accounts;
    @Autowired PasswordEncoder passwords;
    @Autowired DoorService doorService;
    @Autowired DoorRepository doors;
    @Autowired DeviceRepository devices;
    @Autowired DeviceTransportCredentialService credentials;
    @Autowired DeviceTransportCredentialRepository keys;
    @Autowired DoorConfigRepository configs;
    @Autowired SystemPinRepository pins;
    @Autowired WebApplicationContext context;
    @Autowired LoginService login;

    private Account account(Account.Role role) {
        var a=new Account(); a.setUsername("user-"+UUID.randomUUID()); a.setDisplayName("Onboarding test");
        a.setPasswordHash(passwords.encode("test-password-123")); a.setRole(role);
        return accounts.saveAndFlush(a);
    }
    private record Kit(Account technician,String first,String second,String request,Map<String,Object> result) {}
    private Kit kit() {
        var tech=account(Account.Role.TECHNICIAN);
        var first=service.register(tech.getId(),Device.NodeType.NODE_1);
        var second=service.register(tech.getId(),Device.NodeType.NODE_2);
        String request=UUID.randomUUID().toString();
        return new Kit(tech,first.getUnitCode(),second.getUnitCode(),request,
                service.createKit(tech.getId(),request,"Bộ thử",null,first.getUnitCode(),second.getUnitCode()));
    }
    @Test void customerCannotCreateInventoryEvenWithValidJwt() throws Exception {
        var customer=account(Account.Role.CUSTOMER);
        var token=login.login(new LoginRequest(customer.getUsername(),"test-password-123")).accessToken();
        MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build()
                .perform(post("/api/v1/technician/inventory").header("Authorization","Bearer "+token)
                    .contentType(MediaType.APPLICATION_JSON).content("{\"nodeType\":\"NODE_1\"}"))
                .andExpect(status().isForbidden());
        assertThatThrownBy(() -> service.register(customer.getId(),Device.NodeType.NODE_1)).isInstanceOf(AccessDeniedException.class);
    }
    @Test void kitRetryDoesNotCreateDuplicateDoorAndBoardsCannotBeRebound() {
        var kit=kit();
        var retry=service.createKit(kit.technician.getId(),kit.request,"Bộ thử",null,kit.first,kit.second);
        assertThat(retry.get("doorId")).isEqualTo(kit.result.get("doorId"));
        assertThatThrownBy(() -> service.createKit(kit.technician.getId(),UUID.randomUUID().toString(),"Other",null,kit.first,kit.second))
                .isInstanceOf(ConflictException.class);
        assertThat(devices.findAllByDoorId((Long)retry.get("doorId"))).hasSize(2);
    }
    @Test void invalidRoleOrForeignInventoryCannotBeAssigned() {
        var tech=account(Account.Role.TECHNICIAN);
        var first=service.register(tech.getId(),Device.NodeType.NODE_1);
        var second=service.register(tech.getId(),Device.NodeType.NODE_1);
        assertThatThrownBy(() -> service.createKit(tech.getId(),UUID.randomUUID().toString(),"Test",null,first.getUnitCode(),second.getUnitCode()))
                .isInstanceOf(BadRequestException.class);
        var other=account(Account.Role.TECHNICIAN);
        assertThatThrownBy(() -> service.factory(other.getId(),first.getUnitCode())).isInstanceOf(AccessDeniedException.class);
    }
    @Test void deviceReceivesBindingOnlyAfterClaimAndWithItsOwnBootstrapSecret() {
        var kit=kit(); var owner=account(Account.Role.CUSTOMER);
        String secret=credentials.enrollmentSecret("bootstrap",kit.first);
        assertThatThrownBy(() -> service.enroll(kit.first,"incorrect-secret")).isInstanceOf(AuthenticationRequiredException.class);
        assertThatThrownBy(() -> service.enroll(kit.first,secret)).isInstanceOf(ConflictException.class);
        Long doorId=(Long)kit.result.get("doorId");
        doorService.claimDoor(owner.getId(),(String)kit.result.get("activationCode"));
        assertThatThrownBy(() -> service.factory(kit.technician.getId(),kit.first)).isInstanceOf(AccessDeniedException.class);
        var enrollment=service.enroll(kit.first,secret);
        assertThat(enrollment.get("doorId")).isEqualTo(doorId);
        assertThat(enrollment.get("deviceCode")).isEqualTo(kit.first);
        assertThat(enrollment.get("mqttHost")).isEqualTo("192.0.2.10");
        assertThat(service.setupNodes(owner.getId(),doorId)).hasSize(2);
        assertThatThrownBy(() -> service.setupNodes(kit.technician.getId(),doorId)).isInstanceOf(ResourceNotFoundException.class);
        var node=devices.findByDeviceCode(kit.first).orElseThrow();
        credentials.revoke(node.getId());
        assertThatThrownBy(() -> service.enroll(kit.first,secret)).isInstanceOf(ConflictException.class);
    }
    @Test void completionRequiresActualReadinessAndIsIdempotent() {
        var kit=kit(); var owner=account(Account.Role.CUSTOMER); Long doorId=(Long)kit.result.get("doorId");
        doorService.claimDoor(owner.getId(),(String)kit.result.get("activationCode"));
        assertThatThrownBy(() -> service.complete(owner.getId(),doorId)).isInstanceOf(ConflictException.class);
        var config=configs.findById(doorId).orElseThrow();
        var pin=new SystemPin(); pin.setDoorId(doorId); pin.setPinHash(passwords.encode("123456")); pins.saveAndFlush(pin);
        for (var node:devices.findAllByDoorId(doorId)) {
            node.setAppliedConfigVersion(config.getVersion()); node.setAppliedPinVersion(config.getCredentialsVersion());
            var key=keys.findById(node.getId()).orElseThrow(); key.setVerifiedAt(LocalDateTime.now()); keys.saveAndFlush(key);
        }
        assertThat(service.complete(owner.getId(),doorId).get("status").toString()).isEqualTo("ACTIVE");
        assertThat(service.complete(owner.getId(),doorId).get("status").toString()).isEqualTo("ACTIVE");
    }
}
