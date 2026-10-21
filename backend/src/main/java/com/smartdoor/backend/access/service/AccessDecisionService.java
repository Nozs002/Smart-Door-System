package com.smartdoor.backend.access.service;

import com.smartdoor.backend.access.entity.AccessLog;
import com.smartdoor.backend.access.entity.LockoutStatus;
import com.smartdoor.backend.access.entity.LockoutStatusId;
import com.smartdoor.backend.access.repository.AccessLogRepository;
import com.smartdoor.backend.access.repository.LockoutStatusRepository;
// Tương lai sẽ import: com.smartdoor.backend.credentials.service.CredentialVerificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccessDecisionService {

    private final AccessLogRepository accessLogRepository;
    private final LockoutStatusRepository lockoutStatusRepository;
    
    // Inject CredentialVerificationService từ module credentials sau
    
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int LOCKOUT_MINUTES = 15;

    @Transactional
    public boolean processAccessRequest(String eventId, Long doorId, LockoutStatusId.Method lockoutMethod, String payload) {
        // 1. Chống ghi trùng bằng Idempotency Key[cite: 11, 14]
        if (accessLogRepository.existsByEventId(eventId)) {
            log.warn("Bỏ qua sự kiện trùng lặp eventId: {}", eventId);
            return false;
        }

        // 2. Map sang method của AccessLog để ghi log[cite: 11]
        AccessLog.Method logMethod = AccessLog.Method.valueOf(lockoutMethod.name());
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

        // 3. Tra cứu khóa tạm bằng PESSIMISTIC_WRITE để tránh xung đột đồng thời[cite: 15]
        LockoutStatusId id = new LockoutStatusId(doorId, lockoutMethod);
        LockoutStatus lockout = lockoutStatusRepository.findByIdForUpdate(id)
                .orElseGet(() -> {
                    LockoutStatus newStatus = new LockoutStatus();
                    newStatus.setId(id);
                    newStatus.setFailedCount(0);
                    return newStatus;
                });

        // Kiểm tra xem cửa có đang bị khóa bởi phương thức này không[cite: 12]
        if (lockout.getLockedUntil() != null && lockout.getLockedUntil().isAfter(now)) {
            saveAccessLog(eventId, doorId, logMethod, AccessLog.Result.DENIED, "Door is temporarily locked");
            return false;
        }

        // 4. Gọi module Credentials để xác thực thật (ở đây giả lập trả về true)
        // boolean isValid = credentialVerificationService.verify(doorId, lockoutMethod, payload);
        boolean isValid = true; 

        // 5. Cập nhật bộ đếm failedCount[cite: 12]
        if (isValid) {
            lockout.setFailedCount(0);
            lockout.setLockedUntil(null);
            saveAccessLog(eventId, doorId, logMethod, AccessLog.Result.GRANTED, "Authentication successful");
        } else {
            lockout.setFailedCount(lockout.getFailedCount() + 1);
            if (lockout.getFailedCount() >= MAX_FAILED_ATTEMPTS) {
                lockout.setLockedUntil(now.plusMinutes(LOCKOUT_MINUTES));
                saveAccessLog(eventId, doorId, logMethod, AccessLog.Result.DENIED, "Too many failed attempts, locked");
                // TODO: Gọi qua module alert để phát tín hiệu cảnh báo
            } else {
                saveAccessLog(eventId, doorId, logMethod, AccessLog.Result.DENIED, "Invalid credential");
            }
        }
        lockoutStatusRepository.save(lockout);

        return isValid;
    }

    private void saveAccessLog(String eventId, Long doorId, AccessLog.Method method, AccessLog.Result result, String reason) {
        AccessLog logRecord = new AccessLog();
        logRecord.setEventId(eventId);
        logRecord.setDoorId(doorId);
        logRecord.setMethod(method);
        logRecord.setResult(result);
        logRecord.setReason(reason);
        logRecord.setOccurredAt(LocalDateTime.now(ZoneOffset.UTC));
        // receivedAt sẽ tự động được set qua @PrePersist[cite: 11]
        accessLogRepository.save(logRecord);
    }
}