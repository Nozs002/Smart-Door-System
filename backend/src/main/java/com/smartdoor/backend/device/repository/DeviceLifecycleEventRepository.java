package com.smartdoor.backend.device.repository;

import com.smartdoor.backend.device.entity.DeviceLifecycleEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Chỉ service của module sở hữu sử dụng; truy vấn không thay thế authorization. */
public interface DeviceLifecycleEventRepository extends JpaRepository<DeviceLifecycleEvent, Long> {

	boolean existsByEventId(String eventId);

	Page<DeviceLifecycleEvent> findAllByDoorIdOrderByOccurredAtDescIdDesc(Long doorId, Pageable pageable);

	Page<DeviceLifecycleEvent> findAllByDeviceIdAndDoorIdOrderByOccurredAtDescIdDesc(
			Long deviceId, Long doorId, Pageable pageable);

}
