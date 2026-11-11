package com.smartdoor.backend.access.service;

import com.smartdoor.backend.access.entity.DoorTelemetry;
import com.smartdoor.backend.access.repository.DoorTelemetryRepository;
import com.smartdoor.backend.device.entity.Device;
import com.smartdoor.backend.device.repository.DeviceRepository;
import com.smartdoor.backend.device.service.DeviceOperationalService;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MonitoringService {
    private final DoorTelemetryRepository readings;
    private final DeviceOperationalService operational;
    private final DoorRepository doors;
    private final DoorOwnershipService ownership;
    private final DeviceRepository devices;
    private final DeviceConnectionPolicy connections;
    private final Clock clock;
    public MonitoringService(DoorTelemetryRepository readings,DeviceOperationalService operational,DoorRepository doors,
        DoorOwnershipService ownership,DeviceRepository devices,DeviceConnectionPolicy connections,Clock clock) {
        this.readings=readings; this.operational=operational; this.doors=doors; this.ownership=ownership;
        this.devices=devices; this.connections=connections; this.clock=clock;
    }
    @Transactional
    public void receive(AuthenticatedDeviceIdentity identity,Sample s) {
        if (s==null || s.schemaVersion()!=1 || s.eventId()==null || s.observedAt()==null
            || !List.of("OPEN","CLOSED","UNKNOWN").contains(Objects.toString(s.doorState(),""))
            || !List.of("LOCKED","UNLOCKED","UNKNOWN").contains(Objects.toString(s.lockState(),""))
            || !valid(s.temperature(),-40,125) || !valid(s.humidity(),0,100)) throw new BadRequestException("Mẫu cảm biến không hợp lệ.");
        try { if (!UUID.fromString(s.eventId()).toString().equals(s.eventId())) throw new IllegalArgumentException(); }
        catch (IllegalArgumentException e) { throw new BadRequestException("eventId phải là UUID chuẩn."); }
        Door door=doors.findByIdForUpdate(identity.doorId()).orElseThrow(() -> new BadRequestException("Không tìm thấy cửa."));
        var source=operational.requireActive(identity);
        if (source.nodeType()!=Device.NodeType.NODE_2 || door.getStatus()!=Door.Status.ACTIVE || door.getOwnerAccountId()==null)
            throw new ConflictException("Chỉ Node 2 của cửa đang hoạt động được gửi số đo.");
        LocalDateTime observed=LocalDateTime.ofInstant(s.observedAt(),ZoneOffset.UTC).truncatedTo(ChronoUnit.MILLIS);
        var old=readings.findByEventId(s.eventId()).orElse(null);
        if (old!=null) {
            if (!old.getDoorId().equals(identity.doorId()) || !old.getSourceDeviceId().equals(source.deviceId())
                || !old.getObservedAt().equals(observed) || !Objects.equals(old.getTemperature(),s.temperature())
                || !Objects.equals(old.getHumidity(),s.humidity()) || !old.getDoorState().equals(s.doorState()) || !old.getLockState().equals(s.lockState()))
                throw new ConflictException("eventId cảm biến đã được sử dụng.");
            return;
        }
        if (s.observedAt().isBefore(clock.instant().minusSeconds(60)) || s.observedAt().isAfter(clock.instant().plusSeconds(5)))
            throw new BadRequestException("Mẫu cảm biến đã cũ hoặc có thời điểm tương lai.");
        DoorTelemetry r=new DoorTelemetry(); r.setEventId(s.eventId()); r.setDoorId(identity.doorId());
        r.setSourceDeviceId(source.deviceId()); r.setTemperature(s.temperature()); r.setHumidity(s.humidity());
        r.setDoorState(s.doorState()); r.setLockState(s.lockState()); r.setObservedAt(observed);
        r.setReceivedAt(LocalDateTime.ofInstant(clock.instant(),ZoneOffset.UTC)); readings.save(r);
    }
    @Transactional(readOnly=true)
    public View latest(Long accountId,Long doorId) {
        Door door=ownership.getOwnedDoor(accountId,doorId);
        return readings.findFirstByDoorIdOrderByObservedAtDescIdDesc(doorId).map(r -> {
            var node=devices.findById(r.getSourceDeviceId()).orElse(null);
            boolean fresh=door.getStatus()==Door.Status.ACTIVE && node!=null && node.getLifecycleStatus()==Device.LifecycleStatus.ACTIVE
                && connections.status(node.getLastHeartbeatAt())==ConnectionStatus.ONLINE
                && !r.getObservedAt().isBefore(LocalDateTime.ofInstant(clock.instant().minusSeconds(60),ZoneOffset.UTC));
            return new View(r.getTemperature(),r.getHumidity(),r.getDoorState(),r.getLockState(),
                r.getObservedAt().toInstant(ZoneOffset.UTC),r.getReceivedAt().toInstant(ZoneOffset.UTC),fresh);
        }).orElse(null);
    }
    private boolean valid(Double v,double min,double max) { return v==null || Double.isFinite(v) && v>=min && v<=max; }
    public record Sample(int schemaVersion,String eventId,Instant observedAt,Double temperature,Double humidity,String doorState,String lockState) { }
    public record View(Double temperature,Double humidity,String doorState,String lockState,Instant observedAt,Instant receivedAt,boolean fresh) { }
}
