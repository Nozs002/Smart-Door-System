package com.smartdoor.backend.identity.service;

import com.smartdoor.backend.identity.dto.ChangePasswordRequest;
import com.smartdoor.backend.identity.entity.Account;
import com.smartdoor.backend.identity.entity.RevokedAccessToken;
import com.smartdoor.backend.identity.repository.AccountRepository;
import com.smartdoor.backend.identity.repository.RevokedAccessTokenRepository;
import com.smartdoor.backend.shared.error.AuthenticationRequiredException;
import com.smartdoor.backend.shared.error.BadRequestException;
import com.smartdoor.backend.shared.security.AccountPrincipal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountSessionService {
    private final AccountRepository accounts;
    private final RevokedAccessTokenRepository revoked;
    private final PasswordEncoder passwords;
    private final Clock clock;

    public AccountSessionService(AccountRepository accounts, RevokedAccessTokenRepository revoked,
            PasswordEncoder passwords, Clock clock) {
        this.accounts = accounts; this.revoked = revoked; this.passwords = passwords; this.clock = clock;
    }

    @Transactional
    public void changePassword(AccountPrincipal principal, ChangePasswordRequest request) {
        Account account = lockedAccount(principal);
        if (request == null || request.currentPassword() == null || request.newPassword() == null
                || !request.isCurrentPasswordWithinPolicy() || !request.isNewPasswordWithinPolicy()
                || request.currentPassword().isEmpty() || request.newPassword().isEmpty()) {
            throw new BadRequestException("Thông tin mật khẩu không hợp lệ.");
        }
        if (!passwords.matches(request.currentPassword(), account.getPasswordHash())) {
            throw new BadRequestException("Mật khẩu hiện tại không đúng.");
        }
        if (passwords.matches(request.newPassword(), account.getPasswordHash())) {
            throw new BadRequestException("Mật khẩu mới phải khác mật khẩu hiện tại.");
        }
        account.setPasswordHash(passwords.encode(request.newPassword()));
        account.setAuthVersion(account.getAuthVersion() + 1);
        accounts.saveAndFlush(account);
    }

    @Transactional
    public void logout(AccountPrincipal principal) {
        lockedAccount(principal);
        if (principal.tokenId() == null || principal.tokenId().isBlank() || principal.tokenId().length() > 100
                || principal.tokenExpiresAt() == null || !principal.tokenExpiresAt().isAfter(clock.instant())) {
            throw new AuthenticationRequiredException();
        }
        if (!revoked.existsById(principal.tokenId())) {
            RevokedAccessToken token = new RevokedAccessToken();
            token.setTokenId(principal.tokenId());
            token.setAccountId(principal.accountId());
            token.setExpiresAt(LocalDateTime.ofInstant(principal.tokenExpiresAt(), ZoneOffset.UTC));
            token.setRevokedAt(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
            revoked.saveAndFlush(token);
        }
    }

    @Scheduled(fixedDelayString = "${app.identity.revocation-cleanup-ms:3600000}", initialDelay = 60000)
    @Transactional
    public void removeExpiredRevocations() {
        revoked.deleteByExpiresAtLessThanEqual(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
    }

    private Account lockedAccount(AccountPrincipal principal) {
        if (principal == null || principal.accountId() == null) throw new AuthenticationRequiredException();
        Account account = accounts.findByIdForUpdate(principal.accountId()).orElseThrow(AuthenticationRequiredException::new);
        if (account.getStatus() != Account.Status.ACTIVE || account.getAuthVersion() != principal.authVersion()) {
            throw new AuthenticationRequiredException();
        }
        return account;
    }
}
