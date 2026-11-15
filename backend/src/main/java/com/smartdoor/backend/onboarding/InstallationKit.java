package com.smartdoor.backend.onboarding;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Entity @Table(name="installation_kits") @Getter @Setter
public class InstallationKit {
    @Id @Column(name="request_id",length=36) private String requestId;
    @Column(name="technician_id",nullable=false,columnDefinition="bigint unsigned") private Long technicianId;
    @Column(name="door_id",nullable=false,columnDefinition="bigint unsigned",unique=true) private Long doorId;
    @Column(name="created_at",nullable=false,columnDefinition="datetime(3)") private LocalDateTime createdAt;
}
