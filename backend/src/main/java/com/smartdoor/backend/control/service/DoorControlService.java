package com.smartdoor.backend.control.service;

import com.smartdoor.backend.control.dto.UnlockCommandPayload;
// Import các infrastructure/mqtt classes do team tự định nghĩa[cite: 7]
// import com.smartdoor.backend.infrastructure.mqtt.MqttPublisher;
// import com.smartdoor.backend.shared.error.AccessDeniedException;
// import com.smartdoor.backend.shared.error.ConflictException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DoorControlService {

    // Giả lập class MqttPublisher từ thư mục infrastructure/mqtt
    // private final MqttPublisher mqttPublisher; 
    
    // Giả lập service kiểm tra quyền từ module Door và Device[cite: 7]
    // private final DoorOwnershipService doorOwnershipService;
    // private final DeviceStatusService deviceStatusService;

    /**
     * Mở cửa từ Mobile App
     */
    public void unlockFromApp(Long doorId, Long accountId) {
        // 1. Kiểm tra quyền sở hữu cửa[cite: 7, 9]
        // boolean isOwner = doorOwnershipService.isOwner(doorId, accountId);
        // if (!isOwner) throw new AccessDeniedException("Bạn không có quyền điều khiển cửa này.");

        // 2. Kiểm tra Node 2 có đang online không (Tránh gửi lệnh vào hư không)
        // if (!deviceStatusService.isNode2Online(doorId)) {
        //      throw new ConflictException("Thiết bị Node 2 hiện đang ngoại tuyến.");
        // }

        // 3. Tạo Command ID (hoặc dùng CorrelationIdFilter có sẵn)
        String commandId = UUID.randomUUID().toString();
        UnlockCommandPayload payload = new UnlockCommandPayload(
                commandId, 
                "UNLOCK", 
                Instant.now().toEpochMilli()
        );

        // 4. Gửi lệnh qua MQTT tới Topic của Node 2
        String topic = String.format("smartdoor/%d/node2/command/unlock", doorId);
        // mqttPublisher.publish(topic, payload, 1); // QoS 1[cite: 2]
        
        log.info("Đã gửi lệnh mở cửa {} tới Node 2, commandId: {}", doorId, commandId);
        
        // Lưu ý: Request này chỉ xác nhận lệnh ĐÃ GỬI (Command Accepted).
        // Phản hồi (ACK) và Timeout vật lý thực sự sẽ được MQTT Handler bắt ở hàm khác[cite: 2].
    }

    /**
     * Xử lý khi có người bấm nút Exit từ bên trong (Node 1)
     */
    public void processExitButton(Long doorId, String eventId) {
        // Gọi sang module 'access' để ghi log sự kiện nút Exit[cite: 7]
        // accessDecisionService.processExit(eventId, doorId);
        
        // Tạo lệnh mở cửa gửi thẳng tới Node 2
        String commandId = eventId; // Dùng chung eventId làm commandId để truy vết
        UnlockCommandPayload payload = new UnlockCommandPayload(commandId, "UNLOCK_EXIT", Instant.now().toEpochMilli());
        
        String topic = String.format("smartdoor/%d/node2/command/unlock", doorId);
        // mqttPublisher.publish(topic, payload, 1);
        
        log.info("Xử lý nút Exit thành công cho cửa {}, eventId: {}", doorId, eventId);
    }
}