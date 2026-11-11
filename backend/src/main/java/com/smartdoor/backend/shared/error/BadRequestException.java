package com.smartdoor.backend.shared.error;

public final class BadRequestException extends ApplicationException {

	public BadRequestException(String message) {
		super(ErrorCode.INVALID_REQUEST, message);
	}
}
