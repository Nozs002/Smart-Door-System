package com.smartdoor.backend.alert.repository;

import com.smartdoor.backend.alert.entity.Alert;
import java.util.Optional;
import java.util.Collection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

/** Chỉ service của module sở hữu sử dụng; truy vấn không thay thế authorization. */
public interface AlertRepository extends JpaRepository<Alert, Long> {

	Optional<Alert> findByIdAndDoorId(Long id, Long doorId);

	Optional<Alert> findByEventId(String eventId);

	@Query("select a.doorId from Alert a where a.id = :id")
	Optional<Long> findDoorIdById(@Param("id") Long id);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select a from Alert a where a.id = :id and a.doorId = :doorId")
	Optional<Alert> lockInDoor(@Param("id") Long id, @Param("doorId") Long doorId);

	Page<Alert> findAllByDoorIdOrderByTriggeredAtDescIdDesc(Long doorId, Pageable pageable);

	Page<Alert> findAllByDoorIdAndStatusOrderByTriggeredAtDescIdDesc(
			Long doorId, Alert.Status status, Pageable pageable);

	Page<Alert> findAllByDoorIdInOrderByTriggeredAtDescIdDesc(Collection<Long> doorIds, Pageable pageable);

	Page<Alert> findAllByDoorIdInAndStatusOrderByTriggeredAtDescIdDesc(
			Collection<Long> doorIds, Alert.Status status, Pageable pageable);

}
