package com.smartdoor.backend.control.service;

import com.smartdoor.backend.control.entity.ControlCommand;
import com.smartdoor.backend.control.repository.ControlCommandRepository;
import com.smartdoor.backend.device.entity.Device;
import com.smartdoor.backend.device.repository.DeviceRepository;
import com.smartdoor.backend.device.service.DeviceConnectionPolicy;
import com.smartdoor.backend.device.dto.DeviceView.ConnectionStatus;
import com.smartdoor.backend.device.service.DeviceHeartbeatService.AuthenticatedDeviceIdentity;
import com.smartdoor.backend.door.entity.Door;
import com.smartdoor.backend.door.repository.DoorRepository;
import com.smartdoor.backend.door.service.DoorOwnershipService;
import com.smartdoor.backend.shared.error.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

@Service
public class UnlockService {
    private final DoorOwnershipService ownership;
    private final DoorRepository doors;
    private final DeviceRepository devices;
    private final ControlCommandRepository commands;
    private final ObjectProvider<UnlockTransport> transport;
    private final Clock clock;
    private final DeviceConnectionPolicy connections;
    public UnlockService(DoorOwnershipService ownership,DoorRepository doors,DeviceRepository devices,
            ControlCommandRepository commands,ObjectProvider<UnlockTransport> transport,Clock clock,DeviceConnectionPolicy connections) {
        this.ownership=ownership; this.doors=doors; this.devices=devices;
        this.commands=commands; this.transport=transport; this.clock=clock; this.connections=connections;
    }
    private LocalDateTime now() { return LocalDateTime.ofInstant(clock.instant(),ZoneOffset.UTC).truncatedTo(ChronoUnit.MILLIS); }
    @Transactional
    public CommandView unlock(Long accountId,Long doorId,UUID requestId) {
        Door door=ownership.requireOwnerForUpdate(accountId,doorId);
        var previous=commands.findById(requestId.toString());
        if (previous.isPresent()) {
            var value=previous.get();
            if (!doorId.equals(value.getDoorId()) || !accountId.equals(value.getAccountId())) throw new ConflictException("Mã lệnh đã được sử dụng.");
            return view(value);
        }
        if (door.getStatus()!=Door.Status.ACTIVE) throw new ConflictException("Cửa chưa hoạt động.");
        var latest=commands.findFirstByDoorIdOrderByCreatedAtDescIdDesc(doorId);
        if (latest.isPresent() && pending(latest.get()) && latest.get().getExpiresAt().isAfter(now()))
            throw new ConflictException("Đang chờ kết quả lệnh trước. Vui lòng kiểm tra lại.");
        var nodes=devices.findAllByDoorIdAndNodeTypeAndLifecycleStatus(doorId,Device.NodeType.NODE_2,Device.LifecycleStatus.ACTIVE);
        if (nodes.size()!=1 || !online(nodes.get(0))) throw new ConflictException("Node 2 chưa hoạt động hoặc đang ngoại tuyến.");
        var channel=transport.getIfAvailable();
        if (channel==null || !channel.connected()) throw new ConflictException("Chưa kết nối được máy chủ MQTT.");
        var command=new ControlCommand();
        command.setId(requestId.toString()); command.setDoorId(doorId); command.setAccountId(accountId);
        command.setDeviceCode(nodes.get(0).getDeviceCode()); command.setStatus("PENDING");
        command.setCreatedAt(now()); command.setExpiresAt(now().plusSeconds(15));
        return view(commands.save(command));
    }
    @Transactional
    public CommandView latest(Long accountId,Long doorId) {
        ownership.requireOwnerForUpdate(accountId,doorId);
        return commands.findFirstByDoorIdOrderByCreatedAtDescIdDesc(doorId).map(c -> { expire(c); return view(c); }).orElse(null);
    }
    @Transactional
    public CommandView get(Long accountId,Long doorId,String id) {
        ownership.requireOwnerForUpdate(accountId,doorId);
        var c=commands.findById(id).filter(v -> doorId.equals(v.getDoorId()) && accountId.equals(v.getAccountId()))
            .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lệnh."));
        expire(c); return view(c);
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public void dispatch(String id) {
        var door=doors.findByIdForUpdate(commands.findDoorId(id).orElseThrow()).orElseThrow();
        var c=commands.findById(id).orElseThrow();
        expire(c);
        if (!"PENDING".equals(c.getStatus())) return;
        var node=devices.findByDeviceCode(c.getDeviceCode()).orElse(null);
        if (door.getStatus()!=Door.Status.ACTIVE || !c.getAccountId().equals(door.getOwnerAccountId())
            || node==null || !c.getDoorId().equals(node.getDoorId()) || node.getNodeType()!=Device.NodeType.NODE_2
            || node.getLifecycleStatus()!=Device.LifecycleStatus.ACTIVE || !online(node)) { c.setStatus("FAILED"); return; }
        // Never automatically retry an unlock after an ambiguous delivery.
        c.setStatus("SENT");
        try {
            var channel=transport.getIfAvailable();
            if (channel==null) throw new IllegalStateException();
            channel.publishUnlock(c.getDoorId(),c.getDeviceCode(),c.getId(),c.getExpiresAt().toInstant(ZoneOffset.UTC).toString());
        } catch (Exception e) { c.setStatus("UNKNOWN"); }
    }
    @Transactional
    public void acknowledge(AuthenticatedDeviceIdentity identity,Ack ack) {
        if (ack==null || ack.schemaVersion()!=1 || ack.commandId()==null
            || !("EXECUTED".equals(ack.result()) || "REJECTED".equals(ack.result()))) throw new BadRequestException("ACK lệnh không hợp lệ.");
        var c=commands.findById(ack.commandId()).orElseThrow(() -> new BadRequestException("Không tìm thấy lệnh ACK."));
        if (!c.getDoorId().equals(identity.doorId()) || !c.getDeviceCode().equals(identity.deviceCode())
            || identity.nodeType()!=Device.NodeType.NODE_2) throw new BadRequestException("Thiết bị không được xác nhận lệnh này.");
        var door=doors.findByIdForUpdate(c.getDoorId()).orElseThrow();
        var node=devices.findByDeviceCode(identity.deviceCode()).orElseThrow();
        if (!c.getDoorId().equals(identity.doorId()) || !c.getDeviceCode().equals(identity.deviceCode())
            || identity.nodeType()!=Device.NodeType.NODE_2 || node.getLifecycleStatus()!=Device.LifecycleStatus.ACTIVE
            || door.getStatus()!=Door.Status.ACTIVE || !c.getAccountId().equals(door.getOwnerAccountId()))
            throw new BadRequestException("Thiết bị không được xác nhận lệnh này.");
        expire(c);
        if (!("SENT".equals(c.getStatus()) || "UNKNOWN".equals(c.getStatus()))) return;
        c.setStatus(ack.result());
        if ("EXECUTED".equals(ack.result())) door.setCommandedLockState(Door.LockState.UNLOCKED);
    }
    public List<String> waiting() {
        return commands.findTop100ByStatusInOrderByCreatedAtAsc(List.of("PENDING","SENT","UNKNOWN")).stream().map(ControlCommand::getId).toList();
    }
    private boolean online(Device node) { return connections.status(node.getLastHeartbeatAt())==ConnectionStatus.ONLINE; }
    private boolean pending(ControlCommand c) { return List.of("PENDING","SENT","UNKNOWN").contains(c.getStatus()); }
    private void expire(ControlCommand c) { if (pending(c) && !c.getExpiresAt().isAfter(now())) c.setStatus("TIMED_OUT"); }
    private CommandView view(ControlCommand c) { return new CommandView(c.getId(),c.getDoorId(),"UNLOCK",c.getStatus(),c.getCreatedAt().toInstant(ZoneOffset.UTC),c.getExpiresAt().toInstant(ZoneOffset.UTC)); }
    public record CommandView(String id,Long doorId,String action,String status,Instant createdAt,Instant expiresAt) { }
    public record Ack(int schemaVersion,String commandId,String result) { }
}
