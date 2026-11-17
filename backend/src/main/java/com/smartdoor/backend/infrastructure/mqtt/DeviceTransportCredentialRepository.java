package com.smartdoor.backend.infrastructure.mqtt;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceTransportCredentialRepository extends JpaRepository<DeviceTransportCredential, Long> {
    List<DeviceTransportCredential> findAllByRevokedAtIsNull();
}
