package com.smartdoor.backend.credentials.repository;

import com.smartdoor.backend.credentials.entity.SystemPin;
import org.springframework.data.jpa.repository.JpaRepository;

/** Chỉ service của module sở hữu sử dụng; truy vấn không thay thế authorization. */
public interface SystemPinRepository extends JpaRepository<SystemPin, Long> {

}
