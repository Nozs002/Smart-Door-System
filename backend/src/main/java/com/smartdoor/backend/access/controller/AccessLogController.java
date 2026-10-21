package com.smartdoor.backend.access.controller;

import com.smartdoor.backend.access.dto.AccessLogDto;
import com.smartdoor.backend.access.repository.AccessLogRepository;
// Tương lai sẽ import DoorService / SecurityUtils để kiểm tra chủ cửa[cite: 9]
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/doors/{doorId}/access-logs")
@RequiredArgsConstructor
public class AccessLogController {

    private final AccessLogRepository accessLogRepository;

    @GetMapping
    public Page<AccessLogDto> getAccessHistory(@PathVariable Long doorId, Pageable pageable) {
        // 1. TODO: Lấy Account ID từ security context
        // 2. TODO: Gọi Service kiểm tra quyền xem Account ID có sở hữu doorId này không (Ownership Check)
        // Nếu không có quyền -> throw new AccessDeniedException("Truy cập bị từ chối") để GlobalExceptionHandler xử lý[cite: 5]

        // 3. Trả về lịch sử đã phân trang và sort sẵn từ repository[cite: 14]
        return accessLogRepository.findAllByDoorIdOrderByReceivedAtDescIdDesc(doorId, pageable)
                .map(AccessLogDto::new);
    }
}