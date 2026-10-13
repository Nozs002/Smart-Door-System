package com.smartdoor.backend.credentials.repository;

import com.smartdoor.backend.credentials.entity.Cardholder;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Chỉ service của module sở hữu sử dụng; truy vấn không thay thế authorization. */
public interface CardholderRepository extends JpaRepository<Cardholder, Long> {

	Page<Cardholder> findAllByAccountId(Long accountId, Pageable pageable);

	Optional<Cardholder> findByIdAndAccountId(Long id, Long accountId);

}
