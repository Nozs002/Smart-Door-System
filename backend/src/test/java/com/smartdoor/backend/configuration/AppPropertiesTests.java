package com.smartdoor.backend.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class AppPropertiesTests {

	@Autowired
	private AppProperties properties;

	@Test
	void bindsTestProfileProperties() {
		assertThat(properties.jwt().issuer()).isEqualTo("smart-door-test");
		assertThat(properties.jwt().accessTokenTtl()).isEqualTo(Duration.ofMinutes(5));
		assertThat(properties.mqtt().host()).isEqualTo("localhost");
		assertThat(properties.mqtt().port()).isEqualTo(1883);
		assertThat(properties.cors().allowedOrigins()).isEqualTo(List.of("http://localhost"));
	}
}
