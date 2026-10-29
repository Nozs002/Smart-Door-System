package com.smartdoor.backend.infrastructure.mqtt;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity @Table(name="processed_device_messages")
@Getter @Setter @NoArgsConstructor
public class ProcessedDeviceMessage {
    @Id @Column(length=64) private String id;
    @Column(name="device_id", nullable=false, columnDefinition="bigint unsigned") private Long deviceId;
    @Column(name="received_at", nullable=false, columnDefinition="datetime(3)") private LocalDateTime receivedAt;
}
