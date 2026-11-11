package com.smartdoor.backend.door.controller;

import com.smartdoor.backend.door.dto.ClaimDoorRequest;
import com.smartdoor.backend.door.dto.DoorView;
import com.smartdoor.backend.door.dto.UpdateDoorRequest;
import com.smartdoor.backend.door.service.ClaimRateLimiter;
import com.smartdoor.backend.door.service.DoorService;
import com.smartdoor.backend.shared.api.PageResponse;
import com.smartdoor.backend.shared.security.CurrentAccountProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/doors")
public class DoorController {
	private final DoorService doors;
	private final CurrentAccountProvider currentAccount;
	private final ClaimRateLimiter limiter;

	public DoorController(DoorService doors, CurrentAccountProvider currentAccount, ClaimRateLimiter limiter) {
		this.doors = doors; this.currentAccount = currentAccount; this.limiter = limiter;
	}

	@GetMapping
	public PageResponse<DoorView> list(@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return doors.listDoors(currentAccount.requireAccountId(), page, size);
	}

	@GetMapping("/{doorId}")
	public DoorView detail(@PathVariable Long doorId) {
		return doors.getDoor(currentAccount.requireAccountId(), doorId);
	}

	@PatchMapping("/{doorId}")
	public DoorView update(@PathVariable Long doorId, @Valid @RequestBody UpdateDoorRequest request) {
		return doors.updateDoor(currentAccount.requireAccountId(), doorId, request);
	}

	@PostMapping("/claim")
	public DoorView claim(@Valid @RequestBody ClaimDoorRequest request, HttpServletRequest servletRequest) {
		Long accountId = currentAccount.requireAccountId();
		limiter.check(accountId, servletRequest.getRemoteAddr());
		return doors.claimDoor(accountId, request.activationCode());
	}
}
