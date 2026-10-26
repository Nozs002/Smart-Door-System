package com.smartdoor.backend.settings.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record UpdateDoorConfigRequest(
		@NotNull @Min(1) @Max(10) Integer maxFailedAttempts,
		@NotNull @Min(10) @Max(3600) Integer lockoutSeconds,
		@NotNull @DecimalMin("30.0") @DecimalMax("80.0") BigDecimal temperatureThreshold,
		@Min(5) @Max(3600) Integer openTimeoutSeconds) {
}
