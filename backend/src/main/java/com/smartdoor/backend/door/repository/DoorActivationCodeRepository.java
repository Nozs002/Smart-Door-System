package com.smartdoor.backend.door.repository;

import com.smartdoor.backend.door.entity.DoorActivationCode;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Chỉ service của module sở hữu sử dụng; truy vấn không thay thế authorization. */
public interface DoorActivationCodeRepository extends JpaRepository<DoorActivationCode, Long> {
	Optional<DoorActivationCode> findByCodeHash(String codeHash);
	boolean existsByCodeHash(String codeHash);

	/** Chỉ đọc scalar trước khi khóa Door: tránh cache entity mã cũ khi hai claim đồng thời. */
	@Query("select c.doorId from DoorActivationCode c where c.codeHash = :hash")
	Optional<Long> findDoorIdByCodeHash(@Param("hash") String hash);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select c from DoorActivationCode c where c.codeHash = :hash and c.doorId = :doorId")
	Optional<DoorActivationCode> findByHashAndDoorIdForUpdate(@Param("hash") String hash, @Param("doorId") Long doorId);

	List<DoorActivationCode> findAllByDoorIdAndConsumedAtIsNullAndRevokedAtIsNullAndExpiresAtAfter(
			Long doorId, LocalDateTime now);

	/** Xác minh hash và hạn mã trong cùng transaction, không lookup bằng mã rõ. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select c from DoorActivationCode c where c.id = :id and c.doorId = :doorId")
	Optional<DoorActivationCode> findByIdAndDoorIdForUpdate(@Param("id") Long id, @Param("doorId") Long doorId);

}
