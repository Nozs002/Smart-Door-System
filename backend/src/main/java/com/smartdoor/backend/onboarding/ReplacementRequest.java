package com.smartdoor.backend.onboarding;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity @Table(name="replacement_requests") @Getter @Setter @NoArgsConstructor
public class ReplacementRequest {
    @Id @Column(length=36) private String id;
    @Column(name="door_id",nullable=false,columnDefinition="bigint unsigned") private Long doorId;
    @Column(name="old_device_id",nullable=false,columnDefinition="bigint unsigned") private Long oldDeviceId;
    @Column(name="new_device_id",columnDefinition="bigint unsigned") private Long newDeviceId;
    @Column(name="owner_account_id",nullable=false,columnDefinition="bigint unsigned") private Long ownerAccountId;
    @Column(name="technician_id",columnDefinition="bigint unsigned") private Long technicianId;
    @Column(name="node_type",nullable=false,length=10) private String nodeType;
    @Column(name="code_hash",nullable=false,length=64,columnDefinition="char(64)") private String codeHash;
    @Column(nullable=false,length=16) private String status;
    @Column(nullable=false,length=500) private String reason;
    @Column(name="created_at",nullable=false,columnDefinition="datetime(3)") private LocalDateTime createdAt;
    @Column(name="expires_at",nullable=false,columnDefinition="datetime(3)") private LocalDateTime expiresAt;
    @Column(name="completed_at",columnDefinition="datetime(3)") private LocalDateTime completedAt;
}
