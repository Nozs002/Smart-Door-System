package com.smartdoor.backend.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

class SecurityConfigurationTests {

	private final SecurityConfiguration configuration = new SecurityConfiguration();

	@Test
	void createsBcryptPasswordEncoder() {
		PasswordEncoder encoder = configuration.passwordEncoder();
		String encoded = encoder.encode("correct-horse-battery-staple");

		assertThat(encoded).startsWith("$2");
		assertThat(encoder.matches("correct-horse-battery-staple", encoded)).isTrue();
		assertThat(encoder.matches("wrong-password", encoded)).isFalse();
	}

	@Test
	void createsCorsAllowlistFromTypedConfiguration() {
		AppProperties properties = new AppProperties(
				new AppProperties.Jwt("issuer", "audience", "a-secret-that-is-at-least-32-characters", Duration.ofMinutes(15)),
				new AppProperties.Mqtt("localhost", 1883, "user", "password", Duration.ofSeconds(10)),
				new AppProperties.Cors(List.of("https://mobile.example.com")));
		CorsConfigurationSource source = configuration.corsConfigurationSource(properties);
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/doors");

		CorsConfiguration cors = source.getCorsConfiguration(request);

		assertThat(cors).isNotNull();
		assertThat(cors.getAllowedOrigins()).containsExactly("https://mobile.example.com");
		assertThat(cors.getAllowedMethods()).contains("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");
		assertThat(cors.getAllowCredentials()).isTrue();
	}
}
