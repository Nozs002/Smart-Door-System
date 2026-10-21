package com.smartdoor.backend.access.controller;

import com.smartdoor.backend.access.entity.LockoutStatusId;
import com.smartdoor.backend.access.service.AccessDecisionService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/access")
@RequiredArgsConstructor
public class AccessRequestController {

    private final AccessDecisionService accessDecisionService;

    @PostMapping("/request")
    public ResponseEntity<Boolean> handleAccessRequest(@RequestBody AccessRequestDto dto) {
        LockoutStatusId.Method method = LockoutStatusId.Method.valueOf(dto.getMethod().toUpperCase());
        
        boolean result = accessDecisionService.processAccessRequest(
                dto.getEventId(),
                dto.getDoorId(),
                method,
                dto.getPayload()
        );
        
        return ResponseEntity.ok(result);
    }

    @Data
    public static class AccessRequestDto {
        private String eventId;
        private Long doorId;
        private String method;
        private String payload;
    }
}