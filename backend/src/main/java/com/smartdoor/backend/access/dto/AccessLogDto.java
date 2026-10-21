package com.smartdoor.backend.access.dto;

import com.smartdoor.backend.access.entity.AccessLog;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AccessLogDto {
    private Long id;
    private String eventId;
    private Long doorId;
    private String method;
    private String result;
    private String reason;
    private LocalDateTime occurredAt;

    // Helper constructor để map từ Entity sang DTO
    public AccessLogDto(AccessLog entity) {
        this.id = entity.getId();
        this.eventId = entity.getEventId();
        this.doorId = entity.getDoorId();
        this.method = entity.getMethod() != null ? entity.getMethod().name() : null;
        this.result = entity.getResult() != null ? entity.getResult().name() : null;
        this.reason = entity.getReason();
        this.occurredAt = entity.getOccurredAt();
    }
}