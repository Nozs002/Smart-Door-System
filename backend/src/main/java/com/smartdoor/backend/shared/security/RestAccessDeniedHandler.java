package com.smartdoor.backend.shared.security;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Profile;
import org.springframework.web.servlet.HandlerExceptionResolver;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
@Profile("!provision & !device-security")
public final class RestAccessDeniedHandler implements AccessDeniedHandler {

	private final HandlerExceptionResolver exceptionResolver;

	public RestAccessDeniedHandler(
			@Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver) {
		this.exceptionResolver = exceptionResolver;
	}

	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response,
			AccessDeniedException accessDeniedException) {
		exceptionResolver.resolveException(request, response, null, accessDeniedException);
	}
}
