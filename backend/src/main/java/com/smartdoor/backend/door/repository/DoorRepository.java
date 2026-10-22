package com.smartdoor.backend.door.repository;

import com.smartdoor.backend.door.entity.Door;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Chỉ service của module sở hữu sử dụng; truy vấn không thay thế authorization. */
public interface DoorRepository extends JpaRepository<Door, Long> {

	Page<Door> findAllByOwnerAccountId(Long accountId, Pageable pageable);

	Optional<Door> findByIdAndOwnerAccountId(Long id, Long accountId);

	boolean existsByIdAndOwnerAccountId(Long id, Long accountId);

	/** Phải gọi trong transaction của service khi claim. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select d from Door d where d.id = :id")
	Optional<Door> findByIdForUpdate(@Param("id") Long id);

}
