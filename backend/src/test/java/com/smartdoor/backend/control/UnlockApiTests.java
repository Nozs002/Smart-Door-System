package com.smartdoor.backend.control;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.smartdoor.backend.control.repository.ControlCommandRepository;
import com.smartdoor.backend.control.service.*;
import com.smartdoor.backend.device.entity.Device;
import com.smartdoor.backend.device.repository.DeviceRepository;
import com.smartdoor.backend.device.service.DeviceHeartbeatService.AuthenticatedDeviceIdentity;
import com.smartdoor.backend.door.entity.Door;
import com.smartdoor.backend.door.repository.DoorRepository;
import com.smartdoor.backend.identity.entity.Account;
import com.smartdoor.backend.identity.repository.AccountRepository;
import com.smartdoor.backend.identity.service.LoginService;
import com.smartdoor.backend.identity.dto.LoginRequest;
import java.time.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest @ActiveProfiles("test") @Import(UnlockApiTests.TransportConfig.class)
class UnlockApiTests {
    @TestConfiguration static class TransportConfig {
        @Bean UnlockTransport unlockTransport() { return new UnlockTransport() {
            public boolean connected() { return true; }
            public void publishUnlock(Long doorId,String deviceCode,String commandId,String expiresAt) { }
            public void publishClose(Long doorId,String deviceCode,String commandId,String expiresAt) { }
        }; }
    }
    @Autowired WebApplicationContext context;
    @Autowired AccountRepository accounts;
    @Autowired DoorRepository doors;
    @Autowired DeviceRepository devices;
    @Autowired ControlCommandRepository commands;
    @Autowired LoginService login;
    @Autowired PasswordEncoder passwords;
    @Autowired UnlockService service;
    @Autowired Clock clock;

    @Test void authenticatedOwnerCanQueueInspectAndConfirmPersistedCommand() throws Exception {
        var mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        var owner=new Account(); owner.setUsername("unlock-"+UUID.randomUUID()); owner.setDisplayName("Unlock test");
        owner.setPasswordHash(passwords.encode("unlock-test-password")); owner=accounts.saveAndFlush(owner);
        var door=new Door(); door.setName("Test door"); door.setOwnerAccountId(owner.getId()); door.setStatus(Door.Status.ACTIVE); door=doors.saveAndFlush(door);
        var node=new Device(); node.setDoorId(door.getId()); node.setDeviceCode("TEST-"+UUID.randomUUID());
        node.setNodeType(Device.NodeType.NODE_2); node.setLifecycleStatus(Device.LifecycleStatus.ACTIVE);
        node.setLastHeartbeatAt(LocalDateTime.now(clock)); devices.saveAndFlush(node);
        String bearer="Bearer "+login.login(new LoginRequest(owner.getUsername(),"unlock-test-password")).accessToken();
        String path="/api/v1/doors/"+door.getId()+"/commands";
        mvc.perform(get(path+"/latest").header("Authorization",bearer)).andExpect(status().isOk()).andExpect(jsonPath("$.data").isEmpty());
        String id=UUID.randomUUID().toString();
        mvc.perform(post(path).header("Authorization",bearer).contentType(MediaType.APPLICATION_JSON).content("{\"requestId\":\""+id+"\"}"))
            .andExpect(status().isAccepted()).andExpect(jsonPath("$.data.id").value(id));
        service.dispatch(id);
        service.acknowledge(new AuthenticatedDeviceIdentity(node.getDeviceCode(),door.getId(),Device.NodeType.NODE_2),new UnlockService.Ack(1,id,"EXECUTED"));
        mvc.perform(get(path+"/"+id).header("Authorization",bearer)).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("EXECUTED"));
        mvc.perform(post(path).header("Authorization",bearer).contentType(MediaType.APPLICATION_JSON).content("{\"requestId\":\""+id+"\"}"))
            .andExpect(status().isAccepted()).andExpect(jsonPath("$.data.status").value("EXECUTED"));
        assertThat(commands.findById(id)).isPresent();
        mvc.perform(post(path).header("Authorization",bearer).contentType(MediaType.APPLICATION_JSON)
            .content("{\"requestId\":\""+id+"\",\"action\":\"CLOSE\"}"))
            .andExpect(status().isConflict());
        String closeId=UUID.randomUUID().toString();
        mvc.perform(post(path).header("Authorization",bearer).contentType(MediaType.APPLICATION_JSON)
            .content("{\"requestId\":\""+closeId+"\",\"action\":\"CLOSE\"}"))
            .andExpect(status().isAccepted()).andExpect(jsonPath("$.data.action").value("CLOSE"));
        assertThat(commands.findById(closeId).orElseThrow().getAction()).isEqualTo("CLOSE");
        service.dispatch(closeId);
        service.acknowledge(new AuthenticatedDeviceIdentity(node.getDeviceCode(),door.getId(),Device.NodeType.NODE_2),new UnlockService.Ack(1,closeId,"EXECUTED"));
        mvc.perform(get(path+"/latest").header("Authorization",bearer))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.action").value("CLOSE"))
            .andExpect(jsonPath("$.data.status").value("EXECUTED"));
        assertThat(doors.findById(door.getId()).orElseThrow().getCommandedLockState()).isEqualTo(Door.LockState.LOCKED);
        var holdOpen=doors.findById(door.getId()).orElseThrow();
        holdOpen.setOperatingMode(Door.OperatingMode.AUTOMATIC); doors.saveAndFlush(holdOpen);
        mvc.perform(post(path).header("Authorization",bearer).contentType(MediaType.APPLICATION_JSON)
            .content("{\"requestId\":\""+UUID.randomUUID()+"\",\"action\":\"CLOSE\"}"))
            .andExpect(status().isConflict());
        mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON)
            .content("{\"requestId\":\""+UUID.randomUUID()+"\",\"action\":\"CLOSE\"}"))
            .andExpect(status().isUnauthorized());
        mvc.perform(post(path).header("Authorization",bearer).contentType(MediaType.APPLICATION_JSON)
            .content("{\"requestId\":\""+UUID.randomUUID()+"\",\"action\":\"OTHER\"}"))
            .andExpect(status().isBadRequest());
        mvc.perform(get(path+"/latest")).andExpect(status().isUnauthorized());
        mvc.perform(post(path).header("Authorization",bearer).contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest());
    }
}
