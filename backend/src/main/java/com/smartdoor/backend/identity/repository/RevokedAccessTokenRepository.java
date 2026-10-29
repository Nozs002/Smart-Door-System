package com.smartdoor.backend.identity.repository;

import com.smartdoor.backend.identity.entity.RevokedAccessToken;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RevokedAccessTokenRepository extends JpaRepository<RevokedAccessToken, String> {
    long deleteByExpiresAtLessThanEqual(LocalDateTime now);
}
