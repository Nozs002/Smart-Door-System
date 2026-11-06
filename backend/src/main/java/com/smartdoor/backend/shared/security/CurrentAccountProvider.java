package com.smartdoor.backend.shared.security;

import com.smartdoor.backend.identity.entity.Account;
import com.smartdoor.backend.identity.repository.AccountRepository;
import com.smartdoor.backend.shared.error.AuthenticationRequiredException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class CurrentAccountProvider {
	private final AccountRepository accounts;

	public CurrentAccountProvider(AccountRepository accounts) {
		this.accounts = accounts;
	}

	public Long requireAccountId() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !authentication.isAuthenticated()
				|| authentication instanceof AnonymousAuthenticationToken
				|| !(authentication.getPrincipal() instanceof AccountPrincipal principal)
				|| principal.accountId() == null || principal.accountId() <= 0) {
			throw new AuthenticationRequiredException();
		}
		return accounts.findById(principal.accountId())
				.filter(account -> account.getStatus() == Account.Status.ACTIVE)
				.map(Account::getId).orElseThrow(AuthenticationRequiredException::new);
	}
}
