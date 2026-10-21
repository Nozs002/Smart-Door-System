package com.smartdoor.backend.control.controller;

import com.smartdoor.backend.control.service.DoorControlService;
// import com.smartdoor.backend.shared.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/doors/{doorId}/control")
@RequiredArgsConstructor
public class DoorControlController {

    private final DoorControlService doorControlService;

    @PostMapping("/unlock")
    public void unlockDoor(@PathVariable Long doorId) {
        // 1. Lấy định danh Account từ Spring Security Context[cite: 9]
        // Long currentAccountId = SecurityUtils.getCurrentAccountId();
        Long currentAccountId = 1L; // Mock tạm thời

        // 2. Thực thi lệnh
        doorControlService.unlockFromApp(doorId, currentAccountId);
        
        // Trả về HTTP 200 OK. 
        // Lớp ApiResponseAdvice sẽ tự bọc thành: {"message": "Thành công", "data": null, ...}[cite: 1]
    }
}