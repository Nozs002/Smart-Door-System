package com.smartdoor.backend.infrastructure.outbox;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface OutboxRepository extends JpaRepository<OutboxMessage, Long> {
	boolean existsByDeviceIdAndKindAndPayloadAndStatusIn(Long deviceId, String kind, String payload,
			Collection<OutboxMessage.Status> statuses);
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select o from OutboxMessage o where o.kind in :kinds and "
			+ "((o.status = :pending and o.nextAttemptAt <= :now) "
			+ "or (o.status = :processing and o.leaseUntil <= :now)) order by o.id")
	List<OutboxMessage> ready(@Param("kinds") Collection<String> kinds,
			@Param("pending") OutboxMessage.Status pending,
			@Param("processing") OutboxMessage.Status processing,
			@Param("now") LocalDateTime now, Pageable pageable);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select o from OutboxMessage o where o.id = :id")
	Optional<OutboxMessage> lock(@Param("id") Long id);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select o from OutboxMessage o where o.doorId = :doorId and "
			+ "(o.status = :failed or o.status = :pending)")
	List<OutboxMessage> retryable(@Param("doorId") Long doorId,
			@Param("failed") OutboxMessage.Status failed, @Param("pending") OutboxMessage.Status pending);

	Page<OutboxMessage> findAllByDoorId(Long doorId, Pageable pageable);
}
