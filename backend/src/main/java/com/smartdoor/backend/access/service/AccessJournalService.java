package com.smartdoor.backend.access.service;

import com.smartdoor.backend.access.entity.AccessLog;
import com.smartdoor.backend.access.repository.AccessLogRepository;
import com.smartdoor.backend.control.entity.ControlCommand;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccessJournalService {
    private final AccessLogRepository logs;
    private final Clock clock;
    public AccessJournalService(AccessLogRepository logs, Clock clock) { this.logs=logs; this.clock=clock; }

    @Transactional
    public void command(ControlCommand c) {
        String eventId=c.getId()+":"+c.getStatus();
        if (logs.existsByEventId(eventId)) return;
        AccessLog log=new AccessLog();
        log.setEventId(eventId); log.setCorrelationId(c.getId()); log.setDoorId(c.getDoorId());
        log.setMethod(AccessLog.Method.valueOf(c.getAccessMethod()));
        if ("APP".equals(c.getAccessMethod())) log.setActorAccountId(c.getAccountId());
        log.setResult(switch(c.getStatus()) {
            case "EXECUTED" -> AccessLog.Result.EXECUTED;
            case "REJECTED", "FAILED", "TIMED_OUT" -> AccessLog.Result.FAILED;
            default -> AccessLog.Result.UNKNOWN;
        });
        log.setReason(("CLOSE".equals(c.getAction()) ? "CLOSE_" : "")+c.getStatus()); log.setOccurredAt(LocalDateTime.ofInstant(clock.instant(),ZoneOffset.UTC));
        logs.save(log);
    }
}
