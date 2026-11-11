package com.smartdoor.backend.shared.observability;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public final class CorrelationIdFilter extends OncePerRequestFilter {

	public static final String HEADER_NAME = "X-Correlation-ID";
	public static final String REQUEST_ATTRIBUTE = CorrelationIdFilter.class.getName() + ".correlationId";
	public static final String MDC_KEY = "correlationId";

	private static final Pattern SAFE_CORRELATION_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		String correlationId = resolveCorrelationId(request.getHeader(HEADER_NAME));
		request.setAttribute(REQUEST_ATTRIBUTE, correlationId);
		response.setHeader(HEADER_NAME, correlationId);

		try (MDC.MDCCloseable ignored = MDC.putCloseable(MDC_KEY, correlationId)) {
			filterChain.doFilter(request, response);
		}
	}

	private String resolveCorrelationId(String candidate) {
		if (candidate != null && SAFE_CORRELATION_ID.matcher(candidate).matches()) {
			return candidate;
		}
		return UUID.randomUUID().toString();
	}
}
