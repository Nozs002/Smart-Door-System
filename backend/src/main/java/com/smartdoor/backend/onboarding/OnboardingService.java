package com.smartdoor.backend.onboarding;

import com.smartdoor.backend.device.entity.Device;
import com.smartdoor.backend.device.repository.DeviceRepository;
import com.smartdoor.backend.device.service.DeviceLifecycleService;
import com.smartdoor.backend.device.service.DeviceReadinessPolicy;
import com.smartdoor.backend.device.dto.LifecycleRequest.Action;
import com.smartdoor.backend.door.entity.Door;
import com.smartdoor.backend.door.repository.DoorRepository;
import com.smartdoor.backend.door.service.DoorProvisioningService;
import com.smartdoor.backend.door.service.DoorOwnershipService;
import com.smartdoor.backend.identity.entity.Account;
import com.smartdoor.backend.identity.repository.AccountRepository;
import com.smartdoor.backend.infrastructure.mqtt.DeviceTransportCredentialService;
import com.smartdoor.backend.shared.error.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import org.springframework.core.env.Environment;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @Transactional
public class OnboardingService {
    private final AccountRepository accounts;
    private final InventoryRepository units;
    private final KitRepository kits;
    private final DoorProvisioningService provision;
    private final DeviceTransportCredentialService credentials;
    private final DeviceRepository devices;
    private final DoorRepository doors;
    private final DoorOwnershipService ownership;
    private final DeviceReadinessPolicy readiness;
    private final DeviceLifecycleService lifecycle;
    private final Environment environment;
    private final Clock clock;

    public OnboardingService(AccountRepository accounts,InventoryRepository units,KitRepository kits,
            DoorProvisioningService provision,DeviceTransportCredentialService credentials,
            DeviceRepository devices,DoorRepository doors,DoorOwnershipService ownership,
            DeviceReadinessPolicy readiness,DeviceLifecycleService lifecycle,Environment environment,Clock clock) {
        this.accounts=accounts; this.units=units; this.kits=kits; this.provision=provision;
        this.credentials=credentials; this.devices=devices; this.doors=doors; this.ownership=ownership;
        this.readiness=readiness; this.lifecycle=lifecycle; this.environment=environment; this.clock=clock;
    }
    public void requireTechnician(Long id) {
        if (accounts.findById(id).filter(a -> a.getStatus()==Account.Status.ACTIVE && a.getRole()==Account.Role.TECHNICIAN).isEmpty())
            throw new AccessDeniedException("Cần tài khoản kỹ thuật viên.");
    }
    public InventoryUnit register(Long accountId,Device.NodeType type) {
        requireTechnician(accountId);
        if (type==null) throw new BadRequestException("Chọn vai trò Node.");
        var unit=new InventoryUnit();
        unit.setUnitCode(UUID.randomUUID().toString()); unit.setNodeType(type);
        unit.setCreatedBy(accountId); unit.setCreatedAt(now());
        return units.saveAndFlush(unit);
    }
    public List<InventoryUnit> inventory(Long accountId) {
        requireTechnician(accountId);
        return units.findAllByCreatedByOrderByCreatedAtDesc(accountId);
    }
    public Map<String,Object> factory(Long accountId,String code) {
        requireTechnician(accountId);
        var unit=unit(code);
        if (!unit.getCreatedBy().equals(accountId)) throw new AccessDeniedException("Thiết bị thuộc kỹ thuật viên khác.");
        if (unit.getDeviceId()!=null) {
            var device=devices.findById(unit.getDeviceId()).orElseThrow();
            if (doors.findById(device.getDoorId()).orElseThrow().getOwnerAccountId()!=null)
                throw new AccessDeniedException("Cửa đã bàn giao. Chủ cửa quản lý mã thiết lập.");
        }
        String bootstrap=credentials.enrollmentSecret("bootstrap",code);
        String setup=setupCode(code);
        String url=environment.getProperty("app.onboarding.bootstrap-url","");
        if (!url.matches("https?://[A-Za-z0-9.:-]+/api/v1/device-enrollment"))
            throw new BadRequestException("Cấu hình DEVICE_BOOTSTRAP_URL trước khi xuất firmware.");
        String ca=environment.getProperty("app.onboarding.bootstrap-ca-pem","");
        if (url.startsWith("https://") && !ca.contains("-----BEGIN CERTIFICATE-----"))
            throw new BadRequestException("HTTPS cần cấu hình DEVICE_BOOTSTRAP_CA_PEM.");
        String escapedCa=ca.replace("\\","\\\\").replace("\"","\\\"").replace("\r","").replace("\n","\\n");
        String header="#pragma once\nnamespace EnrollmentConfig {\n"
                +"constexpr char UNIT_CODE[] = \""+code+"\";\n"
                +"constexpr char BOOTSTRAP_SECRET[] = \""+bootstrap+"\";\n"
                +"constexpr char SETUP_CODE[] = \""+setup+"\";\n"
                +"constexpr char BOOTSTRAP_URL[] = \""+url+"\";\n"
                +"constexpr char CA_CERT[] = \""+escapedCa+"\";\n}\n";
        return Map.of("unitCode",code,"nodeType",unit.getNodeType(),"setupCode",setup,"setupSsid",ssid(code),"firmwareConfig",header);
    }
    public Map<String,Object> createKit(Long accountId,String requestId,String name,String location,String node1,String node2) {
        requireTechnician(accountId);
        try { UUID.fromString(requestId); } catch (RuntimeException e) { throw new BadRequestException("requestId phải là UUID."); }
        // Serialize retries from the same technician before inspecting the request id.
        accounts.findByIdForUpdate(accountId).orElseThrow();
        var previous=kits.findById(requestId);
        if (previous.isPresent()) {
            var kit=previous.get();
            if (!kit.getTechnicianId().equals(accountId)) throw new AccessDeniedException("Yêu cầu thuộc tài khoản khác.");
            var codes=devices.findAllByDoorId(kit.getDoorId()).stream().map(Device::getDeviceCode).toList();
            if (!codes.contains(node1) || !codes.contains(node2) || node1.equals(node2)) throw new ConflictException("requestId đã dùng cho bộ khác.");
            return kitView(kit);
        }
        if (node1==null || node2==null || node1.equals(node2)) throw new BadRequestException("Cần hai Node khác nhau.");
        // Stable lock order also serializes two technicians trying to bind the same boards.
        var ordered=new ArrayList<>(List.of(node1,node2)); Collections.sort(ordered);
        var locked=new HashMap<String,InventoryUnit>();
        for (String code:ordered) locked.put(code,units.lockByCode(code).orElseThrow(() -> new ResourceNotFoundException("Mã thiết bị chưa đăng ký.")));
        var first=locked.get(node1); var second=locked.get(node2);
        for (var unit:locked.values()) {
            if (!unit.getCreatedBy().equals(accountId)) throw new AccessDeniedException("Thiết bị thuộc kỹ thuật viên khác.");
            if (unit.getDeviceId()!=null) throw new ConflictException("Thiết bị đã gắn vào cửa.");
        }
        if (first.getNodeType()!=Device.NodeType.NODE_1 || second.getNodeType()!=Device.NodeType.NODE_2)
            throw new BadRequestException("Sai vai trò Node 1/Node 2.");
        var result=provision.provision(name,location,node1,node2);
        for (var device:devices.findAllByDoorId(result.doorId())) {
            locked.get(device.getDeviceCode()).setDeviceId(device.getId());
            credentials.issue(device.getId());
        }
        var kit=new InstallationKit(); kit.setRequestId(requestId); kit.setTechnicianId(accountId);
        kit.setDoorId(result.doorId()); kit.setCreatedAt(now()); kits.saveAndFlush(kit);
        var response=new LinkedHashMap<>(kitView(kit));
        response.put("activationCode",result.activationCode()); response.put("expiresAt",result.expiresAt());
        return response;
    }
    public List<Map<String,Object>> listKits(Long accountId) {
        requireTechnician(accountId);
        return kits.findAllByTechnicianIdOrderByCreatedAtDesc(accountId).stream().map(this::kitView).toList();
    }
    public Object reissue(Long accountId,String requestId) {
        requireTechnician(accountId);
        var kit=kits.findById(requestId).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bộ cửa."));
        if (!kit.getTechnicianId().equals(accountId)) throw new AccessDeniedException("Bộ cửa thuộc kỹ thuật viên khác.");
        return provision.reissue(kit.getDoorId());
    }
    private Map<String,Object> kitView(InstallationKit kit) {
        var door=doors.findById(kit.getDoorId()).orElseThrow();
        return Map.of("requestId",kit.getRequestId(),"doorId",door.getId(),"name",door.getName(),"status",door.getStatus(),
                "devices",devices.findAllByDoorId(door.getId()).stream().map(d -> Map.of("deviceId",d.getId(),"unitCode",d.getDeviceCode(),"nodeType",d.getNodeType())).toList());
    }
    public Map<String,Object> enroll(String code,String secret) {
        var unit=unit(code);
        String expected=credentials.enrollmentSecret("bootstrap",code);
        if (secret==null || !MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),secret.getBytes(StandardCharsets.UTF_8)))
            throw new AuthenticationRequiredException();
        if (unit.getDeviceId()==null) throw new ConflictException("Thiết bị chưa được gắn vào cửa.");
        var device=devices.findById(unit.getDeviceId()).orElseThrow();
        var door=doors.findByIdForUpdate(device.getDoorId()).orElseThrow();
        if (door.getOwnerAccountId()==null) throw new ConflictException("Cửa chưa được nhận quyền sở hữu.");
        var export=credentials.current(device.getId());
        String host=environment.getProperty("app.onboarding.mqtt-public-host","");
        if (host.isBlank()) throw new ConflictException("Máy chủ chưa cấu hình MQTT_PUBLIC_HOST.");
        return Map.of("deviceId",device.getId(),"doorId",door.getId(),"deviceCode",device.getDeviceCode(),"nodeType",device.getNodeType(),
                "credentialId",export.credentialId(),"username",export.username(),"password",export.password(),"signingKey",export.signingKey(),
                "mqttHost",host,"mqttPort",environment.getProperty("app.onboarding.mqtt-public-port",Integer.class,1884));
    }
    public List<Map<String,Object>> setupNodes(Long accountId,Long doorId) {
        ownership.requireOwner(accountId,doorId);
        return units.findAllByDeviceIdIn(devices.findAllByDoorId(doorId).stream().map(Device::getId).toList()).stream()
                .map(u -> Map.<String,Object>of("unitCode",u.getUnitCode(),"nodeType",u.getNodeType(),"deviceId",u.getDeviceId(),
                        "setupCode",setupCode(u.getUnitCode()),"setupSsid",ssid(u.getUnitCode()))).toList();
    }
    public Map<String,Object> complete(Long accountId,Long doorId) {
        var door=ownership.requireOwnerForUpdate(accountId,doorId);
        if (door.getStatus()==Door.Status.DISABLED) throw new ConflictException("Cửa đang vô hiệu hóa.");
        var nodes=devices.findAllByDoorId(doorId).stream().filter(d -> d.getLifecycleStatus()!=Device.LifecycleStatus.RETIRED).toList();
        if (nodes.size()!=2 || nodes.stream().map(Device::getNodeType).distinct().count()!=2 || nodes.stream().anyMatch(d -> !readiness.canActivate(d)))
            throw new ConflictException("Cần đủ hai Node và ACK cấu hình/PIN hiện hành.");
        for (var node:nodes) {
            if (node.getLifecycleStatus()==Device.LifecycleStatus.PROVISIONED)
                lifecycle.changeLifecycle(accountId,doorId,node.getId(),Action.ACTIVATE,null);
            else if (node.getLifecycleStatus()!=Device.LifecycleStatus.ACTIVE) throw new ConflictException("Node đang bị vô hiệu hóa.");
        }
        return Map.of("doorId",doorId,"status",doors.findById(doorId).orElseThrow().getStatus());
    }
    private InventoryUnit unit(String code) { return units.findById(code).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thiết bị.")); }
    private String setupCode(String code) { return credentials.enrollmentSecret("setup",code).substring(0,16); }
    private String ssid(String code) { return "SmartDoor-"+code.substring(0,8); }
    private LocalDateTime now() { return LocalDateTime.ofInstant(clock.instant(),ZoneOffset.UTC); }
}
