package com.smartdoor.backend.device.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record LifecycleRequest(@NotNull Action action, @Size(max = 500) String reason) {
	public enum Action {
		ACTIVATE, DISABLE, REACTIVATE, RETIRE
	}
}
