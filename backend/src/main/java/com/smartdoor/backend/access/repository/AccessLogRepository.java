package com.smartdoor.backend.access.repository;

import com.smartdoor.backend.access.entity.AccessLog;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Chỉ service của module sở hữu sử dụng; truy vấn không thay thế authorization. */
public interface AccessLogRepository extends JpaRepository<AccessLog, Long> {

	boolean existsByEventId(String eventId);

	Page<AccessLog> findAllByDoorIdOrderByReceivedAtDescIdDesc(Long doorId, Pageable pageable);

	List<AccessLog> findAllByDoorIdAndCorrelationIdOrderByReceivedAtAscIdAsc(
			Long doorId, String correlationId);

}
