package com.smartdoor.backend.configuration;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import com.nimbusds.jose.jwk.source.ImmutableSecret;

@Configuration
public class JwtConfiguration {
    @Bean
    Clock identityClock() { return Clock.systemUTC(); }

    private SecretKeySpec key(AppProperties properties) {
        byte[] bytes = properties.jwt().secret().getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32 || properties.jwt().accessTokenTtl().toSeconds() < 1) {
            throw new IllegalArgumentException("JWT requires a 32-byte key and a positive TTL in seconds");
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }
    @Bean
    public JwtEncoder jwtEncoder(AppProperties properties) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(key(properties)));
    }
    @Bean
    public JwtDecoder jwtDecoder(AppProperties properties, Clock clock) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key(properties)).macAlgorithm(MacAlgorithm.HS256).build();
        JwtTimestampValidator timestamps = new JwtTimestampValidator(Duration.ZERO);
        timestamps.setClock(clock);
        OAuth2TokenValidator<Jwt> required = jwt -> jwt.getExpiresAt() != null
                && jwt.getExpiresAt().isAfter(clock.instant())
                && jwt.getIssuedAt() != null && jwt.getSubject() != null
                && jwt.getAudience().contains(properties.jwt().audience())
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(timestamps,
                new JwtIssuerValidator(properties.jwt().issuer()), required));
        return decoder;
    }
}
