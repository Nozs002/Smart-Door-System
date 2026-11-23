package com.smartdoor.backend.access.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Entity @Table(name="door_telemetry") @Getter @Setter
public class DoorTelemetry {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(columnDefinition="bigint unsigned") private Long id;
    @Column(nullable=false,unique=true,length=36) private String eventId;
    @Column(nullable=false,columnDefinition="bigint unsigned") private Long doorId;
    @Column(nullable=false,columnDefinition="bigint unsigned") private Long sourceDeviceId;
    private Double temperature;
    private Double humidity;
    @Column(nullable=false,length=16) private String doorState;
    @Column(nullable=false,length=16) private String lockState;
    @Column(nullable=false,columnDefinition="datetime(3)") private LocalDateTime observedAt;
    @Column(nullable=false,columnDefinition="datetime(3)") private LocalDateTime receivedAt;
}
