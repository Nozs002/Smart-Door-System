package com.smartdoor.backend.device.repository;

import com.smartdoor.backend.device.entity.Device;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Chỉ service của module sở hữu sử dụng; truy vấn không thay thế authorization. */
public interface DeviceRepository extends JpaRepository<Device, Long> {

	Optional<Device> findByDeviceCode(String deviceCode);

	Optional<Device> findByIdAndDoorId(Long id, Long doorId);

	List<Device> findAllByDoorId(Long doorId);

	List<Device> findAllByDoorIdAndNodeTypeAndLifecycleStatus(
			Long doorId, Device.NodeType nodeType, Device.LifecycleStatus lifecycleStatus);

	/** Khóa hàng Node; thay thế còn phải khóa Door để bảo vệ slot. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select d from Device d where d.id = :id and d.doorId = :doorId")
	Optional<Device> findByIdAndDoorIdForUpdate(@Param("id") Long id, @Param("doorId") Long doorId);

}
