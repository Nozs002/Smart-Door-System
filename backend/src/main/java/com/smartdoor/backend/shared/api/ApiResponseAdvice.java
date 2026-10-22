package com.smartdoor.backend.shared.api;

import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import com.smartdoor.backend.shared.observability.CorrelationIdFilter;

@RestControllerAdvice
public final class ApiResponseAdvice implements ResponseBodyAdvice<Object> {

	private static final String ACTUATOR_PATH = "/actuator";

	@Override
	public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
		return true;
	}

	@Override
	public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
			Class<? extends HttpMessageConverter<?>> selectedConverterType, ServerHttpRequest request,
			ServerHttpResponse response) {
		if (shouldSkip(body, selectedContentType, request, response)) {
			return body;
		}

		String correlationId = correlationId(request);
		if (body instanceof ApiResponse<?> apiResponse) {
			return apiResponse.withCorrelationId(correlationId);
		}

		return new ApiResponse<>("Thành công", body, java.time.Instant.now(), correlationId);
	}

	private boolean shouldSkip(Object body, MediaType contentType, ServerHttpRequest request,
			ServerHttpResponse response) {
		if (body instanceof ProblemDetail) {
			return true;
		}
		if (!MediaType.APPLICATION_JSON.isCompatibleWith(contentType)) {
			return true;
		}
		if (request.getURI().getPath().startsWith(ACTUATOR_PATH)) {
			return true;
		}
		if (request.getMethod() != null && "HEAD".equals(request.getMethod().name())) {
			return true;
		}
		return response instanceof ServletServerHttpResponse servletResponse
				&& servletResponse.getServletResponse().getStatus() == HttpStatus.NO_CONTENT.value();
	}

	private String correlationId(ServerHttpRequest request) {
		if (request instanceof ServletServerHttpRequest servletRequest) {
			Object value = servletRequest.getServletRequest()
					.getAttribute(CorrelationIdFilter.REQUEST_ATTRIBUTE);
			if (value instanceof String correlationId && !correlationId.isBlank()) {
				return correlationId;
			}
		}
		return "unavailable";
	}
}
