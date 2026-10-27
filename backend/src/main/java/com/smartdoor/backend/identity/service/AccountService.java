package com.smartdoor.backend.identity.service;

import java.util.Locale;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.smartdoor.backend.identity.dto.AccountProfileResponse;
import com.smartdoor.backend.identity.dto.RegisterRequest;
import com.smartdoor.backend.identity.entity.Account;
import com.smartdoor.backend.identity.repository.AccountRepository;
import com.smartdoor.backend.shared.error.AuthenticationRequiredException;
import com.smartdoor.backend.shared.error.ConflictException;

@Service
public class AccountService {
    private final AccountRepository accounts;
    private final PasswordEncoder passwords;

    public AccountService(AccountRepository accounts, PasswordEncoder passwords) {
        this.accounts = accounts;
        this.passwords = passwords;
    }

    @Transactional
    public AccountProfileResponse register(RegisterRequest request) {
        if (accounts.existsByUsername(request.username())) { throw duplicateUsername(); }
        Account account = new Account();
        account.setUsername(request.username());
        account.setDisplayName(request.displayName());
        account.setPasswordHash(passwords.encode(request.password()));
        account.setStatus(Account.Status.ACTIVE);
        try {
            return AccountProfileResponse.from(accounts.saveAndFlush(account));
        } catch (DataIntegrityViolationException exception) {
            // The database UNIQUE constraint also protects concurrent registrations.
            for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
                if (cause instanceof ConstraintViolationException violation
                        && violation.getConstraintName() != null
                        && violation.getConstraintName().toLowerCase(Locale.ROOT).contains("uk_accounts_username")) {
                    throw duplicateUsername();
                }
            }
            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public AccountProfileResponse profile(String authenticatedSubject) {
        long id;
        try { id = Long.parseLong(authenticatedSubject); }
        catch (IllegalArgumentException exception) { throw new AuthenticationRequiredException(); }
        Account account = accounts.findById(id).orElseThrow(AuthenticationRequiredException::new);
        if (account.getStatus() != Account.Status.ACTIVE) { throw new AuthenticationRequiredException(); }
        return AccountProfileResponse.from(account);
    }

    private ConflictException duplicateUsername() {
        return new ConflictException("Tên đăng nhập đã được sử dụng.");
    }
}
