package com.smartdoor.backend.alert.repository;

import com.smartdoor.backend.alert.entity.Alert;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Chỉ service của module sở hữu sử dụng; truy vấn không thay thế authorization. */
public interface AlertRepository extends JpaRepository<Alert, Long> {

	Optional<Alert> findByIdAndDoorId(Long id, Long doorId);

	Page<Alert> findAllByDoorIdOrderByTriggeredAtDescIdDesc(Long doorId, Pageable pageable);

	Page<Alert> findAllByDoorIdAndStatusOrderByTriggeredAtDescIdDesc(
			Long doorId, Alert.Status status, Pageable pageable);

}
