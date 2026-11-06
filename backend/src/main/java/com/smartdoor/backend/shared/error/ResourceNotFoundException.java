package com.smartdoor.backend.shared.error;

public final class ResourceNotFoundException extends ApplicationException {

	public ResourceNotFoundException(String message) {
		super(ErrorCode.RESOURCE_NOT_FOUND, message);
	}
}
