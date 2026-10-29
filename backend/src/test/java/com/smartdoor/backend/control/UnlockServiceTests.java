package com.smartdoor.backend.control;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import com.smartdoor.backend.control.entity.ControlCommand;
import com.smartdoor.backend.control.repository.ControlCommandRepository;
import com.smartdoor.backend.control.service.*;
import com.smartdoor.backend.device.entity.Device;
import com.smartdoor.backend.device.repository.DeviceRepository;
import com.smartdoor.backend.device.service.DeviceConnectionPolicy;
import com.smartdoor.backend.device.service.DeviceHeartbeatService.AuthenticatedDeviceIdentity;
import com.smartdoor.backend.door.entity.Door;
import com.smartdoor.backend.door.repository.DoorRepository;
import com.smartdoor.backend.door.service.DoorOwnershipService;
import com.smartdoor.backend.shared.error.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.ObjectProvider;

class UnlockServiceTests {
    final DoorRepository doors=mock(DoorRepository.class);
    final DeviceRepository devices=mock(DeviceRepository.class);
    final ControlCommandRepository commands=mock(ControlCommandRepository.class);
    final UnlockTransport transport=mock(UnlockTransport.class);
    @SuppressWarnings("unchecked") final ObjectProvider<UnlockTransport> provider=mock(ObjectProvider.class);
    final Clock clock=Clock.fixed(Instant.parse("2026-10-29T03:00:00Z"),ZoneOffset.UTC);
    final UnlockService service=new UnlockService(new DoorOwnershipService(doors),doors,devices,commands,provider,clock,new DeviceConnectionPolicy(clock,90));
    final Door door=new Door();
    final Device node=new Device();
    final UUID id=UUID.randomUUID();
    @BeforeEach void setup() {
        door.setId(1L); door.setOwnerAccountId(2L); door.setStatus(Door.Status.ACTIVE);
        node.setDoorId(1L); node.setDeviceCode("NODE-2"); node.setNodeType(Device.NodeType.NODE_2);
        node.setLifecycleStatus(Device.LifecycleStatus.ACTIVE); node.setLastHeartbeatAt(LocalDateTime.now(clock));
        when(doors.findByIdForUpdate(1L)).thenReturn(Optional.of(door));
        when(devices.findByDeviceCode("NODE-2")).thenReturn(Optional.of(node));
        when(devices.findAllByDoorIdAndNodeTypeAndLifecycleStatus(1L,Device.NodeType.NODE_2,Device.LifecycleStatus.ACTIVE)).thenReturn(List.of(node));
        when(provider.getIfAvailable()).thenReturn(transport); when(transport.connected()).thenReturn(true);
        when(commands.save(any())).thenAnswer(i -> i.getArgument(0));
    }
    ControlCommand command(String status) {
        var c=new ControlCommand(); c.setId(id.toString()); c.setDoorId(1L); c.setAccountId(2L);
        c.setDeviceCode("NODE-2"); c.setStatus(status); c.setCreatedAt(LocalDateTime.now(clock)); c.setExpiresAt(LocalDateTime.now(clock).plusSeconds(15));
        when(commands.findById(c.getId())).thenReturn(Optional.of(c));
        when(commands.findDoorId(c.getId())).thenReturn(Optional.of(1L));
        return c;
    }
    @Test void ownerQueuesCommandAndDuplicateIdDoesNotPublishAgain() throws Exception {
        assertThat(service.unlock(2L,1L,id).status()).isEqualTo("PENDING");
        var c=command("SENT");
        assertThat(service.unlock(2L,1L,id).id()).isEqualTo(c.getId());
        verify(commands,times(1)).save(any()); verify(transport,never()).publishUnlock(anyLong(),anyString(),anyString(),anyString());
    }
    @Test void wrongOwnerAndOfflineNodeCannotUnlock() {
        assertThatThrownBy(() -> service.unlock(99L,1L,id)).isInstanceOf(ResourceNotFoundException.class);
        node.setLastHeartbeatAt(LocalDateTime.now(clock).minusMinutes(2));
        assertThatThrownBy(() -> service.unlock(2L,1L,id)).isInstanceOf(ConflictException.class);
        verify(commands,never()).save(any());
    }
    @Test void expiredCommandNeverPublishes() throws Exception {
        var c=command("PENDING"); c.setExpiresAt(LocalDateTime.now(clock)); service.dispatch(c.getId());
        assertThat(c.getStatus()).isEqualTo("TIMED_OUT"); verifyNoInteractions(transport);
    }
    @Test void dispatchNeverReplaysSentCommand() throws Exception {
        var c=command("PENDING"); service.dispatch(c.getId()); service.dispatch(c.getId());
        verify(transport,times(1)).publishUnlock(eq(1L),eq("NODE-2"),eq(c.getId()),anyString());
        assertThat(c.getStatus()).isEqualTo("SENT");
    }
    @Test void onlyMatchingActiveNodeCanConfirmBeforeExpiry() {
        var c=command("SENT");
        var ack=new UnlockService.Ack(1,c.getId(),"EXECUTED");
        assertThatThrownBy(() -> service.acknowledge(new AuthenticatedDeviceIdentity("NODE-2",1L,Device.NodeType.NODE_1),ack)).isInstanceOf(BadRequestException.class);
        service.acknowledge(new AuthenticatedDeviceIdentity("NODE-2",1L,Device.NodeType.NODE_2),ack);
        assertThat(c.getStatus()).isEqualTo("EXECUTED"); assertThat(door.getCommandedLockState()).isEqualTo(Door.LockState.UNLOCKED);
        c.setStatus("SENT"); c.setExpiresAt(LocalDateTime.now(clock)); door.setCommandedLockState(null);
        service.acknowledge(new AuthenticatedDeviceIdentity("NODE-2",1L,Device.NodeType.NODE_2),ack);
        assertThat(c.getStatus()).isEqualTo("TIMED_OUT"); assertThat(door.getCommandedLockState()).isNull();
    }
}
