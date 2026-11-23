package com.smartdoor.backend.alert.controller;

import com.smartdoor.backend.alert.dto.AlertView;
import com.smartdoor.backend.alert.dto.UpdateAlertStatusRequest;
import com.smartdoor.backend.alert.entity.Alert;
import com.smartdoor.backend.alert.service.AlertService;
import com.smartdoor.backend.shared.api.PageResponse;
import com.smartdoor.backend.shared.security.CurrentAccountProvider;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/alerts")
public class AlertController {
	private final AlertService alerts;
	private final CurrentAccountProvider currentAccount;

	public AlertController(AlertService alerts, CurrentAccountProvider currentAccount) {
		this.alerts = alerts; this.currentAccount = currentAccount;
	}

	@GetMapping
	public PageResponse<AlertView> list(@RequestParam(required = false) Long doorId,
			@RequestParam(required = false) Alert.Status status,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
		return alerts.list(currentAccount.requireAccountId(), doorId, status, page, size);
	}

	@GetMapping("/{alertId}")
	public AlertView detail(@PathVariable Long alertId) {
		return alerts.detail(currentAccount.requireAccountId(), alertId);
	}

	@PatchMapping("/{alertId}/status")
	public AlertView status(@PathVariable Long alertId, @Valid @RequestBody UpdateAlertStatusRequest request) {
		return alerts.changeStatus(currentAccount.requireAccountId(), alertId, request.status());
	}
}
