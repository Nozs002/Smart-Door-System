package com.smartdoor.backend.onboarding;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReplacementRepository extends JpaRepository<ReplacementRequest,String> {
    Optional<ReplacementRequest> findByCodeHash(String codeHash);
    Optional<ReplacementRequest> findByNewDeviceId(Long deviceId);
    List<ReplacementRequest> findAllByDoorIdOrderByCreatedAtDescIdDesc(Long doorId);
    List<ReplacementRequest> findAllByTechnicianIdOrderByCreatedAtDescIdDesc(Long technicianId);
}
