package com.smartdoor.backend.alert.dto;

import com.smartdoor.backend.alert.entity.Alert;
import jakarta.validation.constraints.NotNull;

public record UpdateAlertStatusRequest(@NotNull Alert.Status status) { }
