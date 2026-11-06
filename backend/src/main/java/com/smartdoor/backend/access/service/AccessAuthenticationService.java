package com.smartdoor.backend.access.service;

import com.smartdoor.backend.access.entity.*;
import com.smartdoor.backend.access.repository.*;
import com.smartdoor.backend.alert.entity.Alert;
import com.smartdoor.backend.alert.service.*;
import com.smartdoor.backend.control.service.UnlockService;
import com.smartdoor.backend.credentials.service.CredentialVerificationService;
import com.smartdoor.backend.device.entity.Device;
import com.smartdoor.backend.device.repository.DeviceRepository;
import com.smartdoor.backend.device.service.DeviceOperationalService;
import com.smartdoor.backend.device.service.DeviceHeartbeatService.AuthenticatedDeviceIdentity;
import com.smartdoor.backend.door.entity.Door;
import com.smartdoor.backend.door.repository.DoorRepository;
import com.smartdoor.backend.infrastructure.outbox.OutboxService;
import com.smartdoor.backend.settings.repository.DoorConfigRepository;
import com.smartdoor.backend.shared.error.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class AccessAuthenticationService {
    private final DoorRepository doors;
    private final DeviceOperationalService operational;
    private final CredentialVerificationService verification;
    private final LockoutStatusRepository lockouts;
    private final AccessLogRepository logs;
    private final DoorConfigRepository configs;
    private final UnlockService unlocks;
    private final HardwareAlertService alerts;
    private final OutboxService outbox;
    private final DeviceRepository devices;
    private final ObjectMapper mapper;
    private final Clock clock;
    public AccessAuthenticationService(DoorRepository doors,DeviceOperationalService operational,CredentialVerificationService verification,
        LockoutStatusRepository lockouts,AccessLogRepository logs,DoorConfigRepository configs,UnlockService unlocks,
        HardwareAlertService alerts,OutboxService outbox,DeviceRepository devices,ObjectMapper mapper,Clock clock) {
        this.doors=doors; this.operational=operational; this.verification=verification; this.lockouts=lockouts;
        this.logs=logs; this.configs=configs; this.unlocks=unlocks; this.alerts=alerts; this.outbox=outbox;
        this.devices=devices; this.mapper=mapper; this.clock=clock;
    }
    @Transactional
    public void receive(AuthenticatedDeviceIdentity identity,Request r) {
        if (r==null || r.schemaVersion()!=1 || r.eventId()==null || r.method()==null || r.occurredAt()==null
            || r.value()==null || r.value().length()>100) throw new BadRequestException("Yêu cầu xác thực không hợp lệ.");
        UUID requestId;
        try { requestId=UUID.fromString(r.eventId()); if (!requestId.toString().equals(r.eventId())) throw new IllegalArgumentException(); }
        catch (IllegalArgumentException e) { throw new BadRequestException("eventId phải là UUID chuẩn."); }
        if (r.occurredAt().isBefore(clock.instant().minusSeconds(15)) || r.occurredAt().isAfter(clock.instant().plusSeconds(5)))
            throw new BadRequestException("Yêu cầu xác thực đã hết hạn.");
        Door door=doors.findByIdForUpdate(identity.doorId()).orElseThrow(() -> new BadRequestException("Không tìm thấy cửa."));
        var source=operational.requireActive(identity);
        if (source.nodeType()!=Device.NodeType.NODE_1 || door.getStatus()!=Door.Status.ACTIVE || door.getOwnerAccountId()==null)
            throw new ConflictException("Chỉ Node 1 của cửa đang hoạt động được yêu cầu xác thực.");
        AccessLog old=logs.findByEventId(r.eventId()).orElse(null);
        if (old!=null) {
            if (!old.getDoorId().equals(door.getId()) || !Objects.equals(old.getSourceDeviceId(),source.deviceId()) || !old.getMethod().name().equals(r.method().name()))
                throw new ConflictException("eventId xác thực đã được sử dụng.");
            respond(identity,old,old.getLockedUntil(),r.occurredAt().plusSeconds(15)); return;
        }
        var id=new LockoutStatusId(door.getId(),r.method());
        var status=lockouts.findByIdForUpdate(id).orElseGet(() -> { var s=new LockoutStatus(); s.setId(id); return s; });
        var config=configs.findById(door.getId()).orElseThrow(() -> new ConflictException("Cửa chưa có cấu hình."));
        var now=LocalDateTime.ofInstant(clock.instant(),ZoneOffset.UTC).truncatedTo(ChronoUnit.MILLIS);
        boolean locked=status.getLockedUntil()!=null && status.getLockedUntil().isAfter(now);
        if (!locked && status.getLockedUntil()!=null) { status.setFailedCount(0); status.setLockedUntil(null); }
        var log=new AccessLog(); log.setEventId(r.eventId()); log.setCorrelationId(r.eventId()); log.setDoorId(door.getId());
        log.setSourceDeviceId(source.deviceId()); log.setMethod(AccessLog.Method.valueOf(r.method().name()));
        log.setOccurredAt(LocalDateTime.ofInstant(r.occurredAt(),ZoneOffset.UTC)); log.setReceivedAt(now);
        if (locked) { log.setResult(AccessLog.Result.DENIED); log.setReason("LOCKED_OUT"); }
        else {
            var checked=r.method()==LockoutStatusId.Method.RFID?verification.verifyRfid(door.getId(),r.value()):verification.verifyPin(door.getId(),r.value());
            log.setRfidCredentialId(checked.credentialId()); log.setCardholderId(checked.cardholderId());
            log.setResult(checked.allowed()?AccessLog.Result.GRANTED:AccessLog.Result.DENIED); log.setReason(checked.reason());
            if (checked.allowed()) {
                status.setFailedCount(0); status.setLockedUntil(null);
                String result=unlocks.unlockFromAccess(door.getOwnerAccountId(),door.getId(),requestId,r.method().name());
                log.setReason(result);
            } else {
                status.setFailedCount(status.getFailedCount()+1);
                if (status.getFailedCount()>=config.getMaxFailedAttempts()) {
                    status.setLockedUntil(now.plusSeconds(config.getLockoutSeconds()));
                    alerts.receive(identity,new HardwareAlertEvent(r.eventId()+":lockout",Alert.Type.TOO_MANY_FAILURES,null,now));
                    for (var node:devices.findAllByDoorIdAndNodeTypeAndLifecycleStatus(door.getId(),Device.NodeType.NODE_2,Device.LifecycleStatus.ACTIVE))
                        outbox.enqueue("ALARM",door.getId(),node.getDeviceCode(),mapper.writeValueAsString(Map.of("schemaVersion",1,"eventId",r.eventId(),"expiresAt",clock.instant().plusSeconds(15).toString())));
                }
            }
        }
        log.setLockedUntil(status.getLockedUntil());
        lockouts.save(status); logs.save(log); respond(identity,log,status.getLockedUntil(),r.occurredAt().plusSeconds(15));
    }
    private void respond(AuthenticatedDeviceIdentity identity,AccessLog log,LocalDateTime lockedUntil,Instant expiresAt) {
        var payload=new LinkedHashMap<String,Object>(); payload.put("schemaVersion",1); payload.put("eventId",log.getEventId());
        payload.put("result",log.getResult()); payload.put("reason",log.getReason());
        payload.put("lockedUntil",lockedUntil==null?null:lockedUntil.toInstant(ZoneOffset.UTC).toString()); payload.put("expiresAt",expiresAt.toString());
        outbox.enqueue("ACCESS_RESULT",identity.doorId(),identity.deviceCode(),mapper.writeValueAsString(payload));
    }
    /** Never include this record in logs: value contains a credential. */
    public record Request(int schemaVersion,String eventId,LockoutStatusId.Method method,String value,Instant occurredAt) {
        @Override public String toString() { return "AccessRequest[redacted]"; }
    }
}
