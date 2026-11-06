package com.smartdoor.backend.device.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record ReplaceDeviceRequest(@NotNull @Positive Long replacementDeviceId,
		@NotBlank @Size(max = 500) String reason) {
}
