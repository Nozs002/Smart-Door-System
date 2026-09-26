package com.smartdoor.backend.door;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DoorSignalHistoryRepository extends JpaRepository<DoorSignalHistory, Long> {

	List<DoorSignalHistory> findTop100ByDeviceIdOrderByReceivedAtDesc(String deviceId);
}
