package com.smartdoor.backend.shared.error;

public final class AuthenticationRequiredException extends ApplicationException {

	public AuthenticationRequiredException() {
		super(ErrorCode.AUTHENTICATION_REQUIRED, "Bạn phải đăng nhập để thực hiện thao tác này.");
	}
}
