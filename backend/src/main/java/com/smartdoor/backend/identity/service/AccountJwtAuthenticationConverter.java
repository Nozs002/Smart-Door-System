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

@Component
public class AccountJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {
    private final AccountRepository accounts;
    public AccountJwtAuthenticationConverter(AccountRepository accounts) { this.accounts = accounts; }
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
        if (account.getStatus() != Account.Status.ACTIVE) { throw new OAuth2AuthenticationException("invalid_token"); }
        return UsernamePasswordAuthenticationToken.authenticated(new AuthenticatedAccount(id), null, List.of());
    }
    private record AuthenticatedAccount(Long accountId) implements AccountPrincipal {}
}
