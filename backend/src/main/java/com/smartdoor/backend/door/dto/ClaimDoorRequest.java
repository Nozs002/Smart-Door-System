package com.smartdoor.backend.door.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ClaimDoorRequest(@NotBlank @Size(max = 32) String activationCode) { }
