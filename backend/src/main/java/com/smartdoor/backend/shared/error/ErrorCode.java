package com.smartdoor.backend.shared.error;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

	INVALID_REQUEST(HttpStatus.BAD_REQUEST, "Yêu cầu không hợp lệ"),
	VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Dữ liệu không hợp lệ"),
	AUTHENTICATION_REQUIRED(HttpStatus.UNAUTHORIZED, "Yêu cầu xác thực"),
	INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Thông tin đăng nhập không hợp lệ"),
	LOGIN_RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "Quá nhiều lần đăng nhập"),
	RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy tài nguyên"),
	CONFLICT(HttpStatus.CONFLICT, "Xung đột dữ liệu"),
	RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "Quá nhiều yêu cầu"),
	ACCESS_DENIED(HttpStatus.FORBIDDEN, "Truy cập bị từ chối"),
	INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Lỗi hệ thống");

	private final HttpStatus status;
	private final String title;

	ErrorCode(HttpStatus status, String title) {
		this.status = status;
		this.title = title;
	}

	public HttpStatus status() {
		return status;
	}

	public String title() {
		return title;
	}
}
