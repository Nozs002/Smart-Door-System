package com.smartdoor.backend.onboarding;

import com.smartdoor.backend.device.entity.Device;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Entity @Table(name="device_inventory") @Getter @Setter
public class InventoryUnit {
    @Id @Column(name="unit_code",length=40) private String unitCode;
    @Enumerated(EnumType.STRING) @Column(name="node_type",nullable=false,length=10) private Device.NodeType nodeType;
    @Column(name="created_by",nullable=false,columnDefinition="bigint unsigned") private Long createdBy;
    @Column(name="device_id",columnDefinition="bigint unsigned",unique=true) private Long deviceId;
    @Column(name="created_at",nullable=false,columnDefinition="datetime(3)") private LocalDateTime createdAt;
}
