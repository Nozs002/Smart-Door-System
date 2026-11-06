package com.smartdoor.backend.access.repository;

import com.smartdoor.backend.access.entity.DoorTelemetry;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DoorTelemetryRepository extends JpaRepository<DoorTelemetry,Long> {
    Optional<DoorTelemetry> findByEventId(String eventId);
    Optional<DoorTelemetry> findFirstByDoorIdOrderByObservedAtDescIdDesc(Long doorId);
}
