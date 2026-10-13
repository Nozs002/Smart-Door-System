package com.smartdoor.backend.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;

import com.smartdoor.backend.shared.error.AuthenticationRequiredException;

class SecurityExceptionHandlersTests {

	@Test
	void delegatesAuthenticationFailureToGlobalExceptionHandling() {
		AtomicReference<Exception> captured = new AtomicReference<>();
		RestAuthenticationEntryPoint entryPoint = new RestAuthenticationEntryPoint(capturingResolver(captured));

		entryPoint.commence(
				new MockHttpServletRequest(),
				new MockHttpServletResponse(),
				new InsufficientAuthenticationException("missing token"));

		assertThat(captured.get()).isInstanceOf(AuthenticationRequiredException.class);
	}

	@Test
	void delegatesAuthorizationFailureToGlobalExceptionHandling() {
		AtomicReference<Exception> captured = new AtomicReference<>();
		RestAccessDeniedHandler handler = new RestAccessDeniedHandler(capturingResolver(captured));
		AccessDeniedException exception = new AccessDeniedException("forbidden");

		handler.handle(new MockHttpServletRequest(), new MockHttpServletResponse(), exception);

		assertThat(captured.get()).isSameAs(exception);
	}

	private HandlerExceptionResolver capturingResolver(AtomicReference<Exception> captured) {
		return (request, response, handler, exception) -> {
			captured.set(exception);
			return new ModelAndView();
		};
	}
}
