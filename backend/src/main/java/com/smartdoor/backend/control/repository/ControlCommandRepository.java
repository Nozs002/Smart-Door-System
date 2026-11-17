package com.smartdoor.backend.control.repository;

import com.smartdoor.backend.control.entity.ControlCommand;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ControlCommandRepository extends JpaRepository<ControlCommand,String> {
    @Query("select c.doorId from ControlCommand c where c.id = :id")
    Optional<Long> findDoorId(@Param("id") String id);
    Optional<ControlCommand> findFirstByDoorIdOrderByCreatedAtDescIdDesc(Long doorId);
    List<ControlCommand> findTop100ByStatusInOrderByCreatedAtAsc(List<String> statuses);
}
