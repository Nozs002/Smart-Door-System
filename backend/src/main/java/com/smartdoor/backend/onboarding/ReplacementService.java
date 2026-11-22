package com.smartdoor.backend.onboarding;

import com.smartdoor.backend.device.dto.LifecycleRequest.Action;
import com.smartdoor.backend.device.entity.Device;
import com.smartdoor.backend.device.repository.DeviceRepository;
import com.smartdoor.backend.device.service.DeviceLifecycleService;
import com.smartdoor.backend.device.service.DeviceReadinessPolicy;
import com.smartdoor.backend.door.entity.Door;
import com.smartdoor.backend.door.service.DoorOwnershipService;
import com.smartdoor.backend.infrastructure.mqtt.DeviceTransportCredentialService;
import com.smartdoor.backend.shared.error.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Owner authorizes a single role; technician binds inventory, owner performs cutover. */
@Service @Transactional
public class ReplacementService {
    private static final Set<String> PENDING=Set.of("OPEN","BOUND","REVOKING");
    private final ReplacementRepository requests;
    private final InventoryRepository inventory;
    private final DeviceRepository devices;
    private final DoorOwnershipService ownership;
    private final OnboardingService onboarding;
    private final DeviceLifecycleService lifecycle;
    private final DeviceReadinessPolicy readiness;
    private final DeviceTransportCredentialService credentials;
    private final Clock clock;
    @PersistenceContext private EntityManager entities;

    public ReplacementService(ReplacementRepository requests,InventoryRepository inventory,DeviceRepository devices,
            DoorOwnershipService ownership,OnboardingService onboarding,DeviceLifecycleService lifecycle,
            DeviceReadinessPolicy readiness,DeviceTransportCredentialService credentials,Clock clock) {
        this.requests=requests; this.inventory=inventory; this.devices=devices; this.ownership=ownership;
        this.onboarding=onboarding; this.lifecycle=lifecycle; this.readiness=readiness; this.credentials=credentials; this.clock=clock;
    }

    public Map<String,Object> create(Long owner,Long doorId,Long oldId,String id,String reason) {
        Door door=ownership.requireOwnerForUpdate(owner,doorId);
        requireDoor(door);
        try { if (!UUID.fromString(id).toString().equals(id)) throw new IllegalArgumentException(); }
        catch (RuntimeException invalid) { throw new BadRequestException("requestId phải là UUID chuẩn."); }
        String text=reason==null ? "" : reason.strip();
        if (text.isEmpty() || text.length()>500) throw new BadRequestException("Nhập lý do thay thiết bị, tối đa 500 ký tự.");
        var previous=requests.findById(id);
        if (previous.isPresent()) {
            var request=previous.get();
            if (!request.getOwnerAccountId().equals(owner) || !request.getDoorId().equals(doorId)
                    || !request.getOldDeviceId().equals(oldId) || !request.getReason().equals(text))
                throw new ConflictException("requestId đã được dùng cho yêu cầu khác.");
            return view(request,true);
        }
        Device old=device(doorId,oldId);
        if (old.getLifecycleStatus()==Device.LifecycleStatus.PROVISIONED)
            throw new ConflictException("Chỉ thay Node đã hoạt động, vô hiệu hóa hoặc ngừng sử dụng.");
        for (var request:requests.findAllByDoorIdOrderByCreatedAtDescIdDesc(doorId)) {
            if (request.getStatus().equals("OPEN") && !request.getExpiresAt().isAfter(now())) request.setStatus("EXPIRED");
            if (request.getNodeType().equals(old.getNodeType().name()) && PENDING.contains(request.getStatus()))
                throw new ConflictException("Vai trò này đã có yêu cầu thay thế. Mở yêu cầu hiện tại hoặc hủy trước khi tạo mới.");
        }
        requests.flush(); // Release MySQL's generated pending-role slot before a new request.
        requireVacantReplacementSlot(old,null);
        var request=new ReplacementRequest();
        request.setId(id); request.setDoorId(doorId); request.setOldDeviceId(oldId); request.setOwnerAccountId(owner);
        request.setNodeType(old.getNodeType().name()); request.setStatus("OPEN"); request.setReason(text);
        request.setCodeHash(hash(code(id))); request.setCreatedAt(now()); request.setExpiresAt(now().plusHours(24));
        requests.saveAndFlush(request);
        return view(request,true);
    }

    public Object current(Long owner,Long doorId,Long oldId) {
        ownership.requireOwner(owner,doorId); device(doorId,oldId);
        return requests.findAllByDoorIdOrderByCreatedAtDescIdDesc(doorId).stream()
            .filter(r -> r.getOldDeviceId().equals(oldId)).findFirst().map(r -> view(r,true)).orElse(null);
    }

    public List<Map<String,Object>> technicianList(Long technician) {
        onboarding.requireTechnician(technician);
        return requests.findAllByTechnicianIdOrderByCreatedAtDescIdDesc(technician).stream().map(r -> view(r,false)).toList();
    }

    public Map<String,Object> bind(Long technician,String authorizationCode,String unitCode) {
        onboarding.requireTechnician(technician);
        var candidate=requests.findByCodeHash(hash(authorizationCode==null ? "" : authorizationCode.strip().toUpperCase(Locale.ROOT)))
            .orElseThrow(() -> new ResourceNotFoundException("Mã ủy quyền không hợp lệ hoặc đã hết hạn."));
        Door door=ownership.requireOwnerForUpdate(candidate.getOwnerAccountId(),candidate.getDoorId());
        requireDoor(door);
        // Another binder may have committed while we waited for the Door lock.
        // Refresh the entity loaded by code lookup instead of returning a stale first-level cache entry.
        entities.refresh(candidate,LockModeType.PESSIMISTIC_WRITE);
        var request=candidate;
        if (!request.getStatus().equals("OPEN")) {
            if (Set.of("BOUND","REVOKING","COMPLETED").contains(request.getStatus())
                    && Objects.equals(request.getTechnicianId(),technician)
                    && device(door.getId(),request.getNewDeviceId()).getDeviceCode().equals(unitCode)) return view(request,false);
            throw new ConflictException("Mã ủy quyền đã được sử dụng hoặc bị hủy.");
        }
        if (!request.getExpiresAt().isAfter(now())) throw new ConflictException("Mã ủy quyền đã hết hạn. Chủ cửa cần cấp mã mới.");
        Device old=device(door.getId(),request.getOldDeviceId());
        requireVacantReplacementSlot(old,null);
        var unit=inventory.lockByCode(unitCode).orElseThrow(() -> new ResourceNotFoundException("Bo chưa đăng ký trong kho."));
        if (!unit.getCreatedBy().equals(technician)) throw new org.springframework.security.access.AccessDeniedException("Bo thuộc kỹ thuật viên khác.");
        if (unit.getDeviceId()!=null) throw new ConflictException("Bo đã gắn với cửa; chọn bo chưa gắn.");
        if (!unit.getNodeType().name().equals(request.getNodeType())) throw new ConflictException("Bo thay thế phải đúng vai trò Node cũ.");
        var replacement=lifecycle.registerReplacement(door.getId(),old.getId(),unit.getUnitCode(),request.getReason());
        credentials.issue(replacement.id());
        unit.setDeviceId(replacement.id()); inventory.saveAndFlush(unit);
        request.setNewDeviceId(replacement.id()); request.setTechnicianId(technician); request.setStatus("BOUND");
        requests.saveAndFlush(request);
        return view(request,false);
    }

    public Map<String,Object> complete(Long owner,Long doorId,String id) {
        Door door=ownership.requireOwnerForUpdate(owner,doorId); requireDoor(door);
        var request=ownedRequest(owner,doorId,id);
        if (request.getStatus().equals("COMPLETED")) return view(request,true);
        if (!Set.of("BOUND","REVOKING").contains(request.getStatus())) throw new ConflictException("Chưa có bo thay thế được kỹ thuật viên gắn.");
        Device old=device(doorId,request.getOldDeviceId()), replacement=device(doorId,request.getNewDeviceId());
        requireVacantReplacementSlot(old,replacement.getId());
        if (!readiness.canActivate(replacement)) {
            if (request.getStatus().equals("REVOKING")) return view(request,true);
            throw new ConflictException("Bo mới cần kết nối và ACK cấu hình/PIN hiện hành trước khi thay.");
        }
        if (request.getStatus().equals("BOUND")) {
            credentials.revoke(old.getId());
            if (old.getLifecycleStatus()==Device.LifecycleStatus.ACTIVE)
                lifecycle.changeLifecycle(owner,doorId,old.getId(),Action.DISABLE,"Bắt đầu thay Node: "+request.getId());
            request.setStatus("REVOKING"); requests.saveAndFlush(request);
        }
        if (!credentials.brokerRevoked(old.getId())) return view(request,true);
        lifecycle.replaceDevice(owner,doorId,old.getId(),replacement.getId(),request.getReason());
        request.setStatus("COMPLETED"); request.setCompletedAt(now()); requests.saveAndFlush(request);
        return view(request,true);
    }

    public Map<String,Object> cancel(Long owner,Long doorId,String id) {
        ownership.requireOwnerForUpdate(owner,doorId);
        var request=ownedRequest(owner,doorId,id);
        if (Set.of("CANCELLED","EXPIRED").contains(request.getStatus())) return view(request,true);
        if (!Set.of("OPEN","BOUND").contains(request.getStatus()))
            throw new ConflictException("Đã bắt đầu thu hồi khóa cũ; cần hoàn tất thay thế, không khôi phục khóa cũ.");
        if (request.getNewDeviceId()!=null)
            lifecycle.cancelStagedReplacement(owner,doorId,request.getNewDeviceId(),"Hủy yêu cầu thay Node: "+request.getReason());
        request.setStatus("CANCELLED"); requests.saveAndFlush(request);
        return view(request,true);
    }

    private ReplacementRequest ownedRequest(Long owner,Long doorId,String id) {
        return requests.findById(id).filter(r -> r.getDoorId().equals(doorId) && r.getOwnerAccountId().equals(owner))
            .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy yêu cầu thay thiết bị."));
    }
    private Device device(Long doorId,Long id) {
        return devices.findByIdAndDoorId(id,doorId).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thiết bị của cửa."));
    }
    private void requireVacantReplacementSlot(Device old,Long allowed) {
        if (devices.findAllByDoorId(old.getDoorId()).stream().anyMatch(d -> d.getNodeType()==old.getNodeType()
                && d.getLifecycleStatus()!=Device.LifecycleStatus.RETIRED && !d.getId().equals(old.getId()) && !d.getId().equals(allowed)))
            throw new ConflictException("Vai trò này đang có Node khác; không tạo thêm bo thay thế trùng vai trò.");
    }
    private static void requireDoor(Door door) {
        if (door.getStatus()==Door.Status.DISABLED) throw new ConflictException("Cửa đang vô hiệu hóa.");
    }
    private Map<String,Object> view(ReplacementRequest request,boolean owner) {
        var result=new LinkedHashMap<String,Object>();
        result.put("id",request.getId()); result.put("doorId",request.getDoorId());
        result.put("oldDeviceId",request.getOldDeviceId()); result.put("newDeviceId",request.getNewDeviceId());
        result.put("doorName",ownership.getOwnedDoor(request.getOwnerAccountId(),request.getDoorId()).getName());
        result.put("nodeType",request.getNodeType()); result.put("status",
            request.getStatus().equals("OPEN") && !request.getExpiresAt().isAfter(now()) ? "EXPIRED" : request.getStatus());
        result.put("oldDeviceCode",device(request.getDoorId(),request.getOldDeviceId()).getDeviceCode());
        if (request.getNewDeviceId()!=null) result.put("newDeviceCode",device(request.getDoorId(),request.getNewDeviceId()).getDeviceCode());
        result.put("reason",request.getReason()); result.put("expiresAt",request.getExpiresAt());
        result.put("completedAt",request.getCompletedAt());
        result.put("brokerRevoked",credentials.brokerRevoked(request.getOldDeviceId()));
        result.put("ready",request.getNewDeviceId()!=null && readiness.canActivate(device(request.getDoorId(),request.getNewDeviceId())));
        if (owner && request.getStatus().equals("OPEN") && request.getExpiresAt().isAfter(now())) result.put("authorizationCode",code(request.getId()));
        if (owner && request.getNewDeviceId()!=null && Set.of("BOUND","REVOKING").contains(request.getStatus()))
            result.put("newNodeSetup",onboarding.setupNodes(request.getOwnerAccountId(),request.getDoorId()).stream()
                .filter(n -> n.get("deviceId").equals(request.getNewDeviceId())).findFirst().orElse(null));
        return result;
    }
    private String code(String id) { return "RPL-"+hash(credentials.enrollmentSecret("replacement",id)).substring(0,20).toUpperCase(Locale.ROOT); }
    private static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private LocalDateTime now() { return LocalDateTime.ofInstant(clock.instant(),ZoneOffset.UTC).truncatedTo(ChronoUnit.MILLIS); }
}
