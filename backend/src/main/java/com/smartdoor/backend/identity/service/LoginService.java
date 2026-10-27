package com.smartdoor.backend.identity.service;

import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.transaction.annotation.Transactional;
import com.smartdoor.backend.configuration.AppProperties;
import com.smartdoor.backend.identity.dto.LoginRequest;
import com.smartdoor.backend.identity.dto.LoginResponse;
import com.smartdoor.backend.identity.entity.Account;
import com.smartdoor.backend.identity.repository.AccountRepository;
import com.smartdoor.backend.shared.error.ApplicationException;
import com.smartdoor.backend.shared.error.ErrorCode;

@Service
public class LoginService {
    private final AccountRepository accounts;
    private final PasswordEncoder passwords;
    private final JwtEncoder encoder;
    private final AppProperties properties;
    private final Clock clock;
    private final String dummyHash;

    public LoginService(AccountRepository accounts, PasswordEncoder passwords, JwtEncoder encoder,
            AppProperties properties, Clock clock) {
        this.accounts = accounts;
        this.passwords = passwords;
        this.encoder = encoder;
        this.properties = properties;
        this.clock = clock;
        this.dummyHash = passwords.encode(UUID.randomUUID().toString());
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        Account account = accounts.findByUsername(request.username()).orElse(null);
        boolean matches = passwords.matches(request.password(), account == null ? dummyHash : account.getPasswordHash());
        if (account == null || !matches || account.getStatus() != Account.Status.ACTIVE) {
            throw new ApplicationException(ErrorCode.INVALID_CREDENTIALS,
                    "Không thể đăng nhập với thông tin đã cung cấp.");
        }
        var now = clock.instant();
        var jwt = properties.jwt();
        var claims = JwtClaimsSet.builder().issuer(jwt.issuer()).audience(List.of(jwt.audience()))
                .subject(account.getId().toString()).issuedAt(now).expiresAt(now.plus(jwt.accessTokenTtl()))
                .id(UUID.randomUUID().toString()).build();
        String token = encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        return new LoginResponse(token, "Bearer", jwt.accessTokenTtl().toSeconds(),
                new LoginResponse.AccountInfo(account.getId(), account.getUsername(), account.getDisplayName()));
    }
}
