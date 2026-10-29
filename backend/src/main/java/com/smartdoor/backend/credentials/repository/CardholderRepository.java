package com.smartdoor.backend.credentials.repository;

import com.smartdoor.backend.credentials.entity.Cardholder;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

/** Chỉ service của module sở hữu sử dụng; truy vấn không thay thế authorization. */
public interface CardholderRepository extends JpaRepository<Cardholder, Long> {

	Page<Cardholder> findAllByAccountId(Long accountId, Pageable pageable);

	Optional<Cardholder> findByIdAndAccountId(Long id, Long accountId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select c from Cardholder c where c.id = :id and c.accountId = :accountId")
	Optional<Cardholder> findOwnedForUpdate(@Param("id") Long id, @Param("accountId") Long accountId);

}
