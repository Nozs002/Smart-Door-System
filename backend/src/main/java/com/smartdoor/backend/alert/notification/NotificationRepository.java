package com.smartdoor.backend.alert.notification;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<OwnerNotification, Long> {
	Optional<OwnerNotification> findByAlertIdAndAccountId(Long alertId, Long accountId);
	Page<OwnerNotification> findAllByAccountId(Long accountId, Pageable pageable);
	Page<OwnerNotification> findAllByAccountIdAndReadAtIsNull(Long accountId, Pageable pageable);
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select n from OwnerNotification n where n.id = :id and n.accountId = :accountId")
	Optional<OwnerNotification> lockOwned(@Param("id") Long id, @Param("accountId") Long accountId);
}
