package com.smartdoor.backend.onboarding;

import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;

public interface InventoryRepository extends JpaRepository<InventoryUnit,String> {
    List<InventoryUnit> findAllByCreatedByOrderByCreatedAtDesc(Long accountId);
    List<InventoryUnit> findAllByDeviceIdIn(List<Long> ids);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from InventoryUnit u where u.unitCode=:code")
    Optional<InventoryUnit> lockByCode(String code);
}
