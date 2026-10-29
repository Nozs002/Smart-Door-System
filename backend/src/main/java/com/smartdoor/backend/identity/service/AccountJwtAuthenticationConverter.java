package com.smartdoor.backend.identity.service;

import java.util.List;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import com.smartdoor.backend.shared.security.AccountPrincipal;
import org.springframework.stereotype.Component;
import com.smartdoor.backend.identity.entity.Account;
import com.smartdoor.backend.identity.repository.AccountRepository;
import com.smartdoor.backend.identity.repository.RevokedAccessTokenRepository;
import java.time.Clock;
import java.time.Instant;

@Component
public class AccountJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {
    private final AccountRepository accounts;
    private final RevokedAccessTokenRepository revoked;
    private final Clock clock;
    public AccountJwtAuthenticationConverter(AccountRepository accounts, RevokedAccessTokenRepository revoked, Clock clock) {
        this.accounts = accounts; this.revoked = revoked; this.clock = clock;
    }
    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Long id;
        try {
            id = Long.valueOf(jwt.getSubject());
            if (id <= 0) { throw new NumberFormatException(); }
        } catch (IllegalArgumentException exception) {
            throw new OAuth2AuthenticationException("invalid_token");
        }
        Account account = accounts.findById(id).orElseThrow(() -> new OAuth2AuthenticationException("invalid_token"));
        Object versionClaim = jwt.getClaim("authVersion");
        if (account.getStatus() != Account.Status.ACTIVE || !(versionClaim instanceof Number version)
                || version.longValue() != account.getAuthVersion() || jwt.getId() == null || jwt.getId().isBlank()
                || jwt.getExpiresAt() == null || !jwt.getExpiresAt().isAfter(clock.instant())
                || revoked.existsById(jwt.getId())) { throw new OAuth2AuthenticationException("invalid_token"); }
        return UsernamePasswordAuthenticationToken.authenticated(
                new AuthenticatedAccount(id, jwt.getId(), jwt.getExpiresAt(), account.getAuthVersion()), null, List.of());
    }
    private record AuthenticatedAccount(Long accountId, String tokenId, Instant tokenExpiresAt,
            long authVersion) implements AccountPrincipal {}
}
