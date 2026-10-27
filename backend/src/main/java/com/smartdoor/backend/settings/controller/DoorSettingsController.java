package com.smartdoor.backend.settings.controller;

import com.smartdoor.backend.settings.dto.DoorConfigView;
import com.smartdoor.backend.settings.dto.UpdateDoorConfigRequest;
import com.smartdoor.backend.settings.service.DeviceCommandPublisher.ChangeReason;
import com.smartdoor.backend.settings.service.DoorSettingsService;
import com.smartdoor.backend.shared.security.CurrentAccountProvider;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/doors/{doorId}")
public class DoorSettingsController {
	private final DoorSettingsService settings;
	private final CurrentAccountProvider currentAccount;

	public DoorSettingsController(DoorSettingsService settings, CurrentAccountProvider currentAccount) {
		this.settings = settings; this.currentAccount = currentAccount;
	}

	@GetMapping("/configuration")
	public DoorConfigView get(@PathVariable Long doorId) {
		return settings.get(currentAccount.requireAccountId(), doorId);
	}

	@PutMapping("/configuration")
	public DoorConfigView update(@PathVariable Long doorId, @Valid @RequestBody UpdateDoorConfigRequest request) {
		return settings.update(currentAccount.requireAccountId(), doorId, request);
	}

	@GetMapping("/sync-status")
	public DoorConfigView syncStatus(@PathVariable Long doorId) {
		return settings.get(currentAccount.requireAccountId(), doorId);
	}

	@PostMapping("/credentials-sync")
	public DoorConfigView credentialsSync(@PathVariable Long doorId, @RequestParam ChangeReason reason) {
		return settings.requestCredentialsSync(currentAccount.requireAccountId(), doorId, reason);
	}
}
