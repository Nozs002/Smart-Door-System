package com.smartdoor.backend.identity;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.sql.SQLException;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.smartdoor.backend.identity.dto.RegisterRequest;
import com.smartdoor.backend.identity.repository.AccountRepository;
import com.smartdoor.backend.identity.service.AccountService;
import com.smartdoor.backend.shared.error.ConflictException;

class AccountServiceTests {
    @Test
    void concurrentUniqueViolationIsMappedToSafeConflict() {
        AccountRepository accounts = mock(AccountRepository.class);
        PasswordEncoder passwords = mock(PasswordEncoder.class);
        when(passwords.encode(any())).thenReturn("hash");
        when(accounts.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("hidden",
                new ConstraintViolationException("hidden", new SQLException("hidden"), "uk_accounts_username")));
        var service = new AccountService(accounts, passwords);
        assertThatThrownBy(() -> service.register(new RegisterRequest("Customer", "customer", "password")))
                .isInstanceOf(ConflictException.class).hasMessage("Tên đăng nhập đã được sử dụng.");
    }
    @Test
    void unrelatedIntegrityFailureIsNotMisreportedAsDuplicateUsername() {
        AccountRepository accounts = mock(AccountRepository.class);
        PasswordEncoder passwords = mock(PasswordEncoder.class);
        when(accounts.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("hidden"));
        var service = new AccountService(accounts, passwords);
        assertThatThrownBy(() -> service.register(new RegisterRequest("Customer", "customer", "password")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
