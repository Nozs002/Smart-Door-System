package com.smartdoor.backend.shared.error;

import java.net.URI;
import java.util.Comparator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.smartdoor.backend.shared.observability.CorrelationIdFilter;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public final class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
	private static final String INTERNAL_ERROR_DETAIL = "The server could not complete the request.";

	@ExceptionHandler(ApplicationException.class)
	ResponseEntity<Object> handleApplicationException(ApplicationException exception, WebRequest request) {
		return response(exception.getErrorCode(), exception.getMessage(), request, null);
	}

	@ExceptionHandler(AccessDeniedException.class)
	ResponseEntity<Object> handleAccessDeniedException(AccessDeniedException exception, WebRequest request) {
		return response(ErrorCode.ACCESS_DENIED, "You do not have permission to perform this action.", request, null);
	}

	@ExceptionHandler(Exception.class)
	ResponseEntity<Object> handleUnexpectedException(Exception exception, WebRequest request) {
		String correlationId = correlationId(request);
		log.error("Unhandled exception; correlationId={}", correlationId, exception);
		return response(ErrorCode.INTERNAL_ERROR, INTERNAL_ERROR_DETAIL, request, null);
	}

	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException exception,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		List<FieldViolation> violations = exception.getBindingResult().getFieldErrors().stream()
				.map(error -> new FieldViolation(error.getField(), safeMessage(error.getDefaultMessage())))
				.sorted(Comparator.comparing(FieldViolation::field).thenComparing(FieldViolation::message))
				.toList();

		return response(ErrorCode.VALIDATION_FAILED, "One or more fields are invalid.", request, violations);
	}

	@Override
	protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException exception,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		return response(ErrorCode.INVALID_REQUEST, "The request body is missing or malformed.", request, null);
	}

	private ResponseEntity<Object> response(ErrorCode errorCode, String detail, WebRequest request,
			List<FieldViolation> violations) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(errorCode.status(), detail);
		problem.setTitle(errorCode.title());
		problem.setType(URI.create("urn:smart-door:error:" + errorCode.name().toLowerCase().replace('_', '-')));
		problem.setInstance(requestUri(request));
		problem.setProperty("errorCode", errorCode.name());
		problem.setProperty("correlationId", correlationId(request));
		if (violations != null && !violations.isEmpty()) {
			problem.setProperty("errors", violations);
		}

		return ResponseEntity.status(errorCode.status())
				.contentType(MediaType.APPLICATION_PROBLEM_JSON)
				.body(problem);
	}

	private URI requestUri(WebRequest request) {
		if (request instanceof ServletWebRequest servletRequest) {
			return URI.create(servletRequest.getRequest().getRequestURI());
		}
		return URI.create("/");
	}

	private String correlationId(WebRequest request) {
		if (request instanceof ServletWebRequest servletRequest) {
			Object value = servletRequest.getRequest().getAttribute(CorrelationIdFilter.REQUEST_ATTRIBUTE);
			if (value instanceof String correlationId && !correlationId.isBlank()) {
				return correlationId;
			}
		}
		return "unavailable";
	}

	private String safeMessage(String message) {
		return message == null || message.isBlank() ? "Invalid value." : message;
	}
}
