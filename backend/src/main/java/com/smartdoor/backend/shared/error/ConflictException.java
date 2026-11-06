package com.smartdoor.backend.shared.error;

public final class ConflictException extends ApplicationException {

	public ConflictException(String message) {
		super(ErrorCode.CONFLICT, message);
	}
}
