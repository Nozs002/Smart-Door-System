package com.smartdoor.backend.shared.security;

import java.security.Principal;

/** Người 1 cung cấp principal này sau khi xác minh JWT; không nhận Account ID từ HTTP body/header. */
public interface AccountPrincipal extends Principal {
	Long accountId();

	@Override
	default String getName() {
		return String.valueOf(accountId());
	}
}
