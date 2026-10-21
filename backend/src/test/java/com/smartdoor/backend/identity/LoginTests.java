package com.smartdoor.backend.identity;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.jwt.*;
import com.smartdoor.backend.configuration.*;
import com.smartdoor.backend.identity.dto.LoginRequest;
import com.smartdoor.backend.identity.entity.Account;
import com.smartdoor.backend.identity.repository.AccountRepository;
import com.smartdoor.backend.identity.service.*;
import com.smartdoor.backend.shared.error.*;

class LoginTests {
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-14T00:00:00Z"), ZoneOffset.UTC);
    private final AppProperties properties = new AppProperties(
            new AppProperties.Jwt("test-issuer", "test-audience", "test-only-secret-at-least-32-bytes-long", Duration.ofMinutes(15)),
            new AppProperties.Mqtt("localhost", 1883, "test", "test", Duration.ofSeconds(10)),
            new AppProperties.Cors(List.of("http://localhost")));
    private final AccountRepository accounts = mock(AccountRepository.class);
    private final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder();
    private final JwtConfiguration configuration = new JwtConfiguration();

    private Account account() {
        Account account = new Account();
        account.setId(7L);
        account.setUsername("customer");
        account.setDisplayName("Khách hàng");
        account.setPasswordHash(passwords.encode("test-password"));
        return account;
    }
    private LoginService service() {
        return new LoginService(accounts, passwords, configuration.jwtEncoder(properties), properties, clock);
    }
    @Test
    void jwtIdentityCanAccessDoorOwnershipBoundary() {
        Account account = account();
        when(accounts.findByUsername("customer")).thenReturn(Optional.of(account));
        when(accounts.findById(7L)).thenReturn(Optional.of(account));
        String token = service().login(new LoginRequest("customer", "test-password")).accessToken();
        var authentication = new AccountJwtAuthenticationConverter(accounts)
                .convert(configuration.jwtDecoder(properties, clock).decode(token));
        var context = org.springframework.security.core.context.SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        org.springframework.security.core.context.SecurityContextHolder.setContext(context);
        try {
            assertThat(authentication.getName()).isEqualTo("7");
            assertThat(new com.smartdoor.backend.shared.security.CurrentAccountProvider(accounts)
                    .requireAccountId()).isEqualTo(7L);
            account.setStatus(Account.Status.DISABLED);
            assertThatThrownBy(() -> new com.smartdoor.backend.shared.security.CurrentAccountProvider(accounts)
                    .requireAccountId()).isInstanceOf(AuthenticationRequiredException.class);
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }

    @Test
    void activeAccountGetsSignedTokenWithIdentityAndExpiry() {
        when(accounts.findByUsername("customer")).thenReturn(Optional.of(account()));
        var result = service().login(new LoginRequest("customer", "test-password"));
        Jwt jwt = configuration.jwtDecoder(properties, clock).decode(result.accessToken());
        assertThat(jwt.getSubject()).isEqualTo("7");
        assertThat(jwt.getExpiresAt()).isEqualTo(clock.instant().plusSeconds(900));
        assertThat(jwt.getClaims()).doesNotContainKeys("password", "passwordHash", "displayName");
        assertThat(result.expiresIn()).isEqualTo(900);
        assertThat(result.tokenType()).isEqualTo("Bearer");
        assertThat(result.toString()).doesNotContain(result.accessToken());
    }
    @Test
    void wrongMissingAndDisabledAccountsHaveSameError() {
        Account account = account();
        when(accounts.findByUsername("customer")).thenReturn(Optional.of(account));
        LoginService service = service();
        assertInvalid(() -> service.login(new LoginRequest("customer", "wrong")));
        account.setStatus(Account.Status.DISABLED);
        assertInvalid(() -> service.login(new LoginRequest("customer", "test-password")));
        when(accounts.findByUsername("missing")).thenReturn(Optional.empty());
        assertInvalid(() -> service.login(new LoginRequest("missing", "test-password")));
    }
    private void assertInvalid(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action).isInstanceOf(ApplicationException.class)
                .hasMessage("Không thể đăng nhập với thông tin đã cung cấp.")
                .satisfies(error -> assertThat(((ApplicationException) error).getErrorCode()).isEqualTo(ErrorCode.INVALID_CREDENTIALS));
    }
    @Test
    void rejectsExpiredWrongAudienceIssuerAndSignature() {
        when(accounts.findByUsername("customer")).thenReturn(Optional.of(account()));
        String token = service().login(new LoginRequest("customer", "test-password")).accessToken();
        assertThatThrownBy(() -> configuration.jwtDecoder(properties,
                Clock.offset(clock, Duration.ofMinutes(16))).decode(token)).isInstanceOf(JwtException.class);
        for (AppProperties.Jwt invalid : List.of(
                new AppProperties.Jwt("other", "test-audience", properties.jwt().secret(), Duration.ofMinutes(15)),
                new AppProperties.Jwt("test-issuer", "other", properties.jwt().secret(), Duration.ofMinutes(15)),
                new AppProperties.Jwt("test-issuer", "test-audience", "another-test-secret-at-least-32-bytes", Duration.ofMinutes(15)))) {
            AppProperties other = new AppProperties(invalid, properties.mqtt(), properties.cors());
            assertThatThrownBy(() -> configuration.jwtDecoder(other, clock).decode(token)).isInstanceOf(JwtException.class);
        }
    }
    @Test
    void disabledAccountCannotUseExistingToken() {
        Account account = account();
        when(accounts.findByUsername("customer")).thenReturn(Optional.of(account));
        when(accounts.findById(7L)).thenReturn(Optional.of(account));
        Jwt jwt = configuration.jwtDecoder(properties, clock)
                .decode(service().login(new LoginRequest("customer", "test-password")).accessToken());
        var converter = new AccountJwtAuthenticationConverter(accounts);
        assertThat(converter.convert(jwt).getName()).isEqualTo("7");
        account.setStatus(Account.Status.DISABLED);
        assertThatThrownBy(() -> converter.convert(jwt)).isInstanceOf(OAuth2AuthenticationException.class);
    }
    @Test
    void limitsUsernameAcrossIpsAndIpAcrossUsernames() {
        var policy = new LoginRateLimitProperties(1, 2, Duration.ofSeconds(60), 100);
        Clock movingClock = mock(Clock.class);
        when(movingClock.instant()).thenReturn(clock.instant());
        var limiter = new LoginRateLimiter(policy, movingClock);
        limiter.check("customer", "ip1");
        assertThatThrownBy(() -> limiter.check("CUSTOMER", "ip2")).isInstanceOf(ApplicationException.class);
        limiter.check("other", "ip1");
        assertThatThrownBy(() -> limiter.check("third", "ip1")).isInstanceOf(ApplicationException.class);
        when(movingClock.instant()).thenReturn(clock.instant().plusSeconds(60));
        limiter.check("customer", "ip1");
    }
    @Test
    void boundsUtf8PasswordWithoutTruncationOrDisclosure() {
        assertThat(new LoginRequest("customer", "ấ".repeat(24)).isPasswordWithinByteLimit()).isTrue();
        assertThat(new LoginRequest("customer", "ấ".repeat(25)).isPasswordWithinByteLimit()).isFalse();
        assertThat(new LoginRequest("customer", "private-value").toString()).doesNotContain("private-value");
    }
}
