package com.smartdoor.backend.shared.security;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.smartdoor.backend.identity.entity.Account;
import com.smartdoor.backend.identity.repository.AccountRepository;
import com.smartdoor.backend.shared.error.AuthenticationRequiredException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class CurrentAccountProviderTests {
	private final AccountRepository accounts = mock(AccountRepository.class);
	private final CurrentAccountProvider provider = new CurrentAccountProvider(accounts);

	@AfterEach void cleanup() { SecurityContextHolder.clearContext(); }

	@Test
	void arbitraryUsernameIsNotTreatedAsAccountId() {
		SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("12", null, List.of()));
		assertThatThrownBy(provider::requireAccountId).isInstanceOf(AuthenticationRequiredException.class);
		verifyNoInteractions(accounts);
	}

	@Test
	void readsVerifiedPrincipalAndRejectsDisabledAccount() {
		AccountPrincipal principal = () -> 12L;
		SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, List.of()));
		Account account = new Account();
		account.setId(12L);
		when(accounts.findById(12L)).thenReturn(Optional.of(account));
		assertThat(provider.requireAccountId()).isEqualTo(12L);
		account.setStatus(Account.Status.DISABLED);
		assertThatThrownBy(provider::requireAccountId).isInstanceOf(AuthenticationRequiredException.class);
	}
}
