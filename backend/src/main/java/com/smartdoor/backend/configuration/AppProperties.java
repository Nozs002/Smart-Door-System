package com.smartdoor.backend.configuration;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(@Valid @NotNull Jwt jwt, @Valid @NotNull Mqtt mqtt, @Valid @NotNull Cors cors) {

	public record Jwt(
			@NotBlank String issuer,
			@NotBlank String audience,
			@NotBlank @Size(min = 32) String secret,
			@NotNull Duration accessTokenTtl) {
	}

	public record Mqtt(
			@NotBlank String host,
			@Min(1) @Max(65535) int port,
			@NotBlank String username,
			@NotBlank String password,
			@NotNull Duration connectionTimeout) {
	}

	public record Cors(@NotEmpty List<@NotBlank String> allowedOrigins) {
	}
}
