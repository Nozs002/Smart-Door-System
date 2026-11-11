package com.smartdoor.backend.infrastructure.mqtt;

import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface ProcessedDeviceMessageRepository extends JpaRepository<ProcessedDeviceMessage, String> {
    @Transactional long deleteByReceivedAtBefore(LocalDateTime cutoff);
}
