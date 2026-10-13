package com.smartdoor.backend.identity.repository;

import com.smartdoor.backend.identity.entity.Account;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Chỉ service của module sở hữu sử dụng; truy vấn không thay thế authorization. */
public interface AccountRepository extends JpaRepository<Account, Long> {

	Optional<Account> findByUsername(String username);

	boolean existsByUsername(String username);

}
