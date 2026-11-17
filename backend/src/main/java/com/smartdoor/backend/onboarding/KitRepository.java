package com.smartdoor.backend.onboarding;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KitRepository extends JpaRepository<InstallationKit,String> {
    List<InstallationKit> findAllByTechnicianIdOrderByCreatedAtDesc(Long accountId);
}
