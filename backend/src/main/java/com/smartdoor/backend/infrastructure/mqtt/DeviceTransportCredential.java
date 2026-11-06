package com.smartdoor.backend.infrastructure.mqtt;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "device_transport_credentials")
@Getter @Setter @NoArgsConstructor
public class DeviceTransportCredential {
    @Id @Column(name="device_id", columnDefinition="bigint unsigned")
    private Long deviceId;
    @Column(name="credential_id", nullable=false, length=36, unique=true)
    private String credentialId;
    @Column(name="verified_at", columnDefinition="datetime(3)")
    private LocalDateTime verifiedAt;
    @Column(name="revoked_at", columnDefinition="datetime(3)")
    private LocalDateTime revokedAt;
    @Column(name="created_at", nullable=false, columnDefinition="datetime(3)")
    private LocalDateTime createdAt;
}
