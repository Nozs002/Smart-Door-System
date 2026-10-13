package com.smartdoor.backend.shared.security;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerExceptionResolver;

import com.smartdoor.backend.shared.error.AuthenticationRequiredException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public final class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

	private final HandlerExceptionResolver exceptionResolver;

	public RestAuthenticationEntryPoint(
			@Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver) {
		this.exceptionResolver = exceptionResolver;
	}

	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response,
			AuthenticationException authenticationException) {
		exceptionResolver.resolveException(request, response, null, new AuthenticationRequiredException());
	}
}
