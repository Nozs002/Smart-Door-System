package com.smartdoor.backend.settings.repository;

import com.smartdoor.backend.settings.entity.DoorConfig;
import org.springframework.data.jpa.repository.JpaRepository;

/** Chỉ service của module sở hữu sử dụng; truy vấn không thay thế authorization. */
public interface DoorConfigRepository extends JpaRepository<DoorConfig, Long> {

}
