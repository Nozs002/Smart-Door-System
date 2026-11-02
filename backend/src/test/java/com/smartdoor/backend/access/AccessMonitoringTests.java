package com.smartdoor.backend.access;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.smartdoor.backend.access.entity.*;
import com.smartdoor.backend.access.repository.*;
import com.smartdoor.backend.access.service.*;
import com.smartdoor.backend.control.repository.ControlCommandRepository;
import com.smartdoor.backend.control.service.*;
import com.smartdoor.backend.credentials.entity.*;
import com.smartdoor.backend.credentials.repository.*;
import com.smartdoor.backend.device.entity.Device;
import com.smartdoor.backend.device.repository.DeviceRepository;
import com.smartdoor.backend.device.service.DeviceHeartbeatService.AuthenticatedDeviceIdentity;
import com.smartdoor.backend.door.entity.Door;
import com.smartdoor.backend.door.repository.DoorRepository;
import com.smartdoor.backend.identity.entity.Account;
import com.smartdoor.backend.identity.repository.AccountRepository;
import com.smartdoor.backend.identity.service.LoginService;
import com.smartdoor.backend.identity.dto.LoginRequest;
import com.smartdoor.backend.settings.entity.DoorConfig;
import com.smartdoor.backend.settings.repository.DoorConfigRepository;
import com.smartdoor.backend.shared.error.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(properties="app.outbox.enabled=false") @ActiveProfiles("test")
@Import(AccessMonitoringTests.Config.class) @Transactional
class AccessMonitoringTests {
    static class MutableClock extends Clock {
        Instant value=Instant.parse("2026-11-02T03:00:00Z");
        public Instant instant() { return value; }
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
    }
    @TestConfiguration static class Config {
        @Bean @Primary MutableClock accessClock() { return new MutableClock(); }
        @Bean UnlockTransport unlockTransport() { return new UnlockTransport() {
            public boolean connected() { return true; }
            public void publishUnlock(Long doorId,String deviceCode,String commandId,String expiresAt) { }
        }; }
    }
    @Autowired MutableClock clock;
    @Autowired AccountRepository accounts;
    @Autowired DoorRepository doors;
    @Autowired DeviceRepository devices;
    @Autowired DoorConfigRepository configs;
    @Autowired SystemPinRepository pins;
    @Autowired CardholderRepository holders;
    @Autowired RfidCredentialRepository rfids;
    @Autowired LockoutStatusRepository lockouts;
    @Autowired AccessLogRepository logs;
    @Autowired DoorTelemetryRepository readings;
    @Autowired AccessAuthenticationService access;
    @Autowired MonitoringService monitoring;
    @Autowired AccessQueryService queries;
    @Autowired UnlockService unlocks;
    @Autowired ControlCommandRepository commands;
    @Autowired PasswordEncoder passwords;
    @Autowired WebApplicationContext context;
    @Autowired LoginService login;
    Account owner;
    Door door;
    Device auth,control;
    AuthenticatedDeviceIdentity identity(Device node) { return new AuthenticatedDeviceIdentity(node.getDeviceCode(),door.getId(),node.getNodeType()); }
    @BeforeEach void setup() {
        clock.value=Instant.now();
        owner=new Account(); owner.setUsername("access-"+UUID.randomUUID()); owner.setDisplayName("Owner");
        owner.setPasswordHash(passwords.encode("test-password")); owner=accounts.saveAndFlush(owner);
        door=new Door(); door.setName("Access door"); door.setOwnerAccountId(owner.getId()); door.setStatus(Door.Status.ACTIVE); door=doors.saveAndFlush(door);
        auth=node(Device.NodeType.NODE_1); control=node(Device.NodeType.NODE_2);
        var config=new DoorConfig(); config.setDoorId(door.getId()); configs.saveAndFlush(config);
        var pin=new SystemPin(); pin.setDoorId(door.getId()); pin.setPinHash(passwords.encode("1234")); pins.saveAndFlush(pin);
    }
    Device node(Device.NodeType type) {
        var n=new Device(); n.setDoorId(door.getId()); n.setDeviceCode("ACCESS-"+UUID.randomUUID()); n.setNodeType(type);
        n.setLifecycleStatus(Device.LifecycleStatus.ACTIVE); n.setLastHeartbeatAt(LocalDateTime.ofInstant(clock.instant(),ZoneOffset.UTC));
        return devices.saveAndFlush(n);
    }
    AccessAuthenticationService.Request request(String value) { return new AccessAuthenticationService.Request(1,UUID.randomUUID().toString(),LockoutStatusId.Method.PIN,value,clock.instant()); }
    @Test void validPinQueuesOneCommandAndAckCreatesSeparateExecutionLog() {
        var r=request("1234"); access.receive(identity(auth),r); access.receive(identity(auth),r);
        assertThat(logs.findAllByDoorIdAndCorrelationIdOrderByReceivedAtAscIdAsc(door.getId(),r.eventId())).hasSize(1);
        assertThat(commands.findById(r.eventId())).isPresent();
        unlocks.dispatch(r.eventId()); unlocks.acknowledge(identity(control),new UnlockService.Ack(1,r.eventId(),"EXECUTED"));
        unlocks.acknowledge(identity(control),new UnlockService.Ack(1,r.eventId(),"EXECUTED"));
        assertThat(logs.findAllByDoorIdAndCorrelationIdOrderByReceivedAtAscIdAsc(door.getId(),r.eventId()))
            .extracting(AccessLog::getResult).containsExactly(AccessLog.Result.GRANTED,AccessLog.Result.EXECUTED);
    }
    @Test void invalidAttemptsLockOutWithoutDuplicateCountingAndExpire() {
        var first=request("9999"); access.receive(identity(auth),first); access.receive(identity(auth),first);
        access.receive(identity(auth),request("9999")); access.receive(identity(auth),request("9999"));
        var state=lockouts.findById(new LockoutStatusId(door.getId(),LockoutStatusId.Method.PIN)).orElseThrow();
        assertThat(state.getFailedCount()).isEqualTo(3); assertThat(state.getLockedUntil()).isAfter(LocalDateTime.ofInstant(clock.instant(),ZoneOffset.UTC));
        var locked=request("1234"); access.receive(identity(auth),locked);
        assertThat(logs.findByEventId(locked.eventId()).orElseThrow().getReason()).isEqualTo("LOCKED_OUT");
        assertThat(commands.findById(locked.eventId())).isEmpty();
        clock.value=clock.instant().plusSeconds(61);
        control.setLastHeartbeatAt(LocalDateTime.ofInstant(clock.instant(),ZoneOffset.UTC)); devices.save(control);
        var after=request("1234"); access.receive(identity(auth),after);
        assertThat(commands.findById(after.eventId())).isPresent(); assertThat(state.getFailedCount()).isZero();
    }
    @Test void unavailableControlDoesNotLoseAuthenticationLogOrRetryUnlock() {
        control.setLastHeartbeatAt(LocalDateTime.ofInstant(clock.instant().minusSeconds(300),ZoneOffset.UTC)); devices.save(control);
        var r=request("1234"); access.receive(identity(auth),r);
        assertThat(logs.findByEventId(r.eventId()).orElseThrow().getReason()).isEqualTo("CONTROL_UNAVAILABLE");
        control.setLastHeartbeatAt(LocalDateTime.ofInstant(clock.instant(),ZoneOffset.UTC)); devices.save(control);
        access.receive(identity(auth),r); assertThat(commands.findById(r.eventId())).isEmpty();
    }
    @Test void rfidGrantIsAttributedToItsCardholderAndRevokedCardIsDenied() {
        var h=new Cardholder(); h.setAccountId(owner.getId()); h.setDisplayName("Nguyen An"); h=holders.saveAndFlush(h);
        var card=new RfidCredential(); card.setDoorId(door.getId()); card.setAccountId(owner.getId()); card.setCardholderId(h.getId()); card.setUidLookupValue("AABBCCDD"); card=rfids.saveAndFlush(card);
        var r=new AccessAuthenticationService.Request(1,UUID.randomUUID().toString(),LockoutStatusId.Method.RFID,"AA:BB:CC:DD",clock.instant());
        access.receive(identity(auth),r);
        assertThat(queries.list(owner.getId(),door.getId(),AccessLog.Method.RFID,AccessLog.Result.GRANTED,0,20).items().getFirst().cardholderName()).isEqualTo("Nguyen An");
        card.setStatus(RfidCredential.Status.REVOKED); rfids.save(card);
        var denied=new AccessAuthenticationService.Request(1,UUID.randomUUID().toString(),LockoutStatusId.Method.RFID,"AABBCCDD",clock.instant());
        access.receive(identity(auth),denied); assertThat(logs.findByEventId(denied.eventId()).orElseThrow().getResult()).isEqualTo(AccessLog.Result.DENIED);
    }
    @Test void staleRequestWrongRoleAndDisabledNodeCannotUnlock() {
        var stale=new AccessAuthenticationService.Request(1,UUID.randomUUID().toString(),LockoutStatusId.Method.PIN,"1234",clock.instant().minusSeconds(16));
        assertThatThrownBy(() -> access.receive(identity(auth),stale)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> access.receive(identity(control),request("1234"))).isInstanceOf(ConflictException.class);
        auth.setLifecycleStatus(Device.LifecycleStatus.DISABLED); devices.save(auth);
        assertThatThrownBy(() -> access.receive(identity(auth),request("1234"))).isInstanceOf(ConflictException.class);
    }
    MonitoringService.Sample sample(Instant at) { return new MonitoringService.Sample(1,UUID.randomUUID().toString(),at,28.5,65.0,"CLOSED","LOCKED"); }
    @Test void telemetryIsIdempotentOrderedAndBecomesStale() {
        var s=sample(clock.instant()); monitoring.receive(identity(control),s); monitoring.receive(identity(control),s);
        monitoring.receive(identity(control),sample(clock.instant().minusSeconds(5)));
        assertThat(monitoring.latest(owner.getId(),door.getId()).observedAt()).isEqualTo(s.observedAt().truncatedTo(java.time.temporal.ChronoUnit.MILLIS));
        assertThat(monitoring.latest(owner.getId(),door.getId()).fresh()).isTrue();
        clock.value=clock.instant().plusSeconds(61); assertThat(monitoring.latest(owner.getId(),door.getId()).fresh()).isFalse();
    }
    @Test void invalidTelemetryAndReusedEventIdAreRejected() {
        var s=sample(clock.instant()); monitoring.receive(identity(control),s);
        assertThatThrownBy(() -> monitoring.receive(identity(control),new MonitoringService.Sample(1,s.eventId(),s.observedAt(),99.0,65.0,"CLOSED","LOCKED"))).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> monitoring.receive(identity(auth),sample(clock.instant()))).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> monitoring.receive(identity(control),new MonitoringService.Sample(1,UUID.randomUUID().toString(),clock.instant(),28.0,101.0,"OPEN","UNKNOWN"))).isInstanceOf(BadRequestException.class);
    }
    @Test void restEndpointsRequireOwnerAndNeverExposeCredentials() throws Exception {
        access.receive(identity(auth),request("1234")); monitoring.receive(identity(control),sample(clock.instant()));
        var mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        String token="Bearer "+login.login(new LoginRequest(owner.getUsername(),"test-password")).accessToken();
        String base="/api/v1/doors/"+door.getId();
        mvc.perform(get(base+"/access-logs").header("Authorization",token)).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items[0].value").doesNotExist()).andExpect(jsonPath("$.data.items[0].result").value("GRANTED"));
        mvc.perform(get(base+"/monitoring").header("Authorization",token)).andExpect(status().isOk()).andExpect(jsonPath("$.data.fresh").value(true));
        mvc.perform(get(base+"/access-summary").header("Authorization",token)).andExpect(status().isOk());
        mvc.perform(get(base+"/access-logs")).andExpect(status().isUnauthorized());
        assertThatThrownBy(() -> queries.list(owner.getId()+999,door.getId(),null,null,0,20)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> monitoring.latest(owner.getId()+999,door.getId())).isInstanceOf(ResourceNotFoundException.class);
    }
}
