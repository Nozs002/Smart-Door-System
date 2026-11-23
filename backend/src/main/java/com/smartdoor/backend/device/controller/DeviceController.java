package com.smartdoor.backend.device.controller;

import com.smartdoor.backend.device.dto.DeviceView;
import com.smartdoor.backend.device.dto.LifecycleEventView;
import com.smartdoor.backend.device.dto.LifecycleRequest;
import com.smartdoor.backend.device.dto.ReplaceDeviceRequest;
import com.smartdoor.backend.device.service.DeviceLifecycleService;
import com.smartdoor.backend.device.service.DeviceQueryService;
import com.smartdoor.backend.shared.api.PageResponse;
import com.smartdoor.backend.shared.security.CurrentAccountProvider;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/doors/{doorId}")
public class DeviceController {

	private final CurrentAccountProvider accounts;
	private final DeviceQueryService queries;
	private final DeviceLifecycleService lifecycle;

	public DeviceController(CurrentAccountProvider accounts, DeviceQueryService queries,
			DeviceLifecycleService lifecycle) {
		this.accounts = accounts;
		this.queries = queries;
		this.lifecycle = lifecycle;
	}

	@GetMapping("/devices")
	public List<DeviceView> listDevices(@PathVariable Long doorId) {
		return queries.listDevices(accounts.requireAccountId(), doorId);
	}

	@GetMapping("/devices/{deviceId}")
	public DeviceView getDevice(@PathVariable Long doorId, @PathVariable Long deviceId) {
		return queries.getDevice(accounts.requireAccountId(), doorId, deviceId);
	}

	@PostMapping("/devices/{deviceId}/lifecycle")
	public DeviceView changeLifecycle(@PathVariable Long doorId, @PathVariable Long deviceId,
			@Valid @RequestBody LifecycleRequest request) {
		return lifecycle.changeLifecycle(accounts.requireAccountId(), doorId, deviceId,
				request.action(), request.reason());
	}

	@PostMapping("/devices/{deviceId}/replace")
	public DeviceView replaceDevice(@PathVariable Long doorId, @PathVariable Long deviceId,
			@Valid @RequestBody ReplaceDeviceRequest request) {
		return lifecycle.replaceDevice(accounts.requireAccountId(), doorId, deviceId,
				request.replacementDeviceId(), request.reason());
	}

	@GetMapping("/lifecycle-events")
	public PageResponse<LifecycleEventView> history(@PathVariable Long doorId,
			@RequestParam(required = false) Long deviceId,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
		return queries.history(accounts.requireAccountId(), doorId, deviceId, page, size);
	}
}
