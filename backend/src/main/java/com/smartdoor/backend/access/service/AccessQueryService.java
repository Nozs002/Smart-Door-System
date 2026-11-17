package com.smartdoor.backend.access.service;

import com.smartdoor.backend.access.entity.AccessLog;
import com.smartdoor.backend.access.repository.AccessLogRepository;
import com.smartdoor.backend.credentials.entity.Cardholder;
import com.smartdoor.backend.credentials.repository.CardholderRepository;
import com.smartdoor.backend.door.service.DoorOwnershipService;
import com.smartdoor.backend.shared.api.PageResponse;
import java.time.LocalDateTime;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @Transactional(readOnly=true)
public class AccessQueryService {
    private final AccessLogRepository logs;
    private final DoorOwnershipService ownership;
    private final CardholderRepository holders;
    public AccessQueryService(AccessLogRepository logs,DoorOwnershipService ownership,CardholderRepository holders) {
        this.logs=logs; this.ownership=ownership; this.holders=holders;
    }
    public PageResponse<LogView> list(Long accountId,Long doorId,AccessLog.Method method,AccessLog.Result result,int page,int size) {
        ownership.requireOwner(accountId,doorId);
        Specification<AccessLog> filter=(root,q,cb) -> cb.and(cb.equal(root.get("doorId"),doorId),
            method==null?cb.conjunction():cb.equal(root.get("method"),method),
            result==null?cb.conjunction():cb.equal(root.get("result"),result));
        return PageResponse.from(logs.findAll(filter,PageResponse.pageable(page,size)).map(l -> view(l,accountId)));
    }
    public Summary summary(Long accountId,Long doorId) {
        ownership.requireOwner(accountId,doorId);
        return new Summary(holders.countByAccountIdAndStatus(accountId,Cardholder.Status.ACTIVE),
            logs.findFirstByDoorIdOrderByReceivedAtDescIdDesc(doorId).map(l -> view(l,accountId)).orElse(null));
    }
    private LogView view(AccessLog l,Long accountId) {
        Long holderId=l.getCardholderId();
        if (holderId==null && l.getMethod()==AccessLog.Method.RFID && l.getCorrelationId()!=null) {
            holderId=logs.findByEventId(l.getCorrelationId()).filter(a -> a.getDoorId().equals(l.getDoorId())).map(AccessLog::getCardholderId).orElse(null);
        }
        String name=holderId==null?null:holders.findByIdAndAccountId(holderId,accountId).map(Cardholder::getDisplayName).orElse(null);
        return new LogView(l.getId(),l.getEventId(),l.getCorrelationId(),l.getMethod(),l.getResult(),l.getReason(),name,l.getOccurredAt(),l.getReceivedAt());
    }
    public record LogView(Long id,String eventId,String correlationId,AccessLog.Method method,AccessLog.Result result,String reason,String cardholderName,LocalDateTime occurredAt,LocalDateTime receivedAt) { }
    public record Summary(long cardholderCount,LogView latestAccess) { }
}
