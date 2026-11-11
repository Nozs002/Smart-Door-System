package com.smartdoor.backend.shared.api;

import java.time.Instant;
import java.util.Objects;

public record ApiResponse<T>(String message, T data, Instant timestamp, String correlationId) {

	private static final String DEFAULT_SUCCESS_MESSAGE = "Thành công";

	public ApiResponse {
		Objects.requireNonNull(message, "message must not be null");
		Objects.requireNonNull(timestamp, "timestamp must not be null");
	}

	public static <T> ApiResponse<T> success(T data) {
		return success(DEFAULT_SUCCESS_MESSAGE, data);
	}

	public static <T> ApiResponse<T> success(String message, T data) {
		return new ApiResponse<>(message, data, Instant.now(), null);
	}

	public ApiResponse<T> withCorrelationId(String value) {
		return new ApiResponse<>(message, data, timestamp, value);
	}
}
