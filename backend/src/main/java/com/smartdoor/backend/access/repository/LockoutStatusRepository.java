package com.smartdoor.backend.access.repository;

import com.smartdoor.backend.access.entity.LockoutStatus;
import com.smartdoor.backend.access.entity.LockoutStatusId;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Chỉ service của module sở hữu sử dụng; truy vấn không thay thế authorization. */
public interface LockoutStatusRepository extends JpaRepository<LockoutStatus, LockoutStatusId> {

	List<LockoutStatus> findAllByIdDoorId(Long doorId);

	/** Khóa bộ đếm để service không tăng đồng thời cùng một lần thử. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select l from LockoutStatus l where l.id = :id")
	Optional<LockoutStatus> findByIdForUpdate(@Param("id") LockoutStatusId id);

}
