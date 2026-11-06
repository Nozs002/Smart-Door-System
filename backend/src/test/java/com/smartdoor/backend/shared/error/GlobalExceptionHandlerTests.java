package com.smartdoor.backend.shared.error;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.smartdoor.backend.shared.observability.CorrelationIdFilter;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

class GlobalExceptionHandlerTests {

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
				.setControllerAdvice(new GlobalExceptionHandler())
				.addFilters(new CorrelationIdFilter())
				.build();
	}

	@Test
	void returnsStableProblemForApplicationException() throws Exception {
		mockMvc.perform(get("/test/resources/{id}", 42).header(CorrelationIdFilter.HEADER_NAME, "request-42"))
				.andExpect(status().isNotFound())
				.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(header().string(CorrelationIdFilter.HEADER_NAME, "request-42"))
				.andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"))
				.andExpect(jsonPath("$.title").value("Không tìm thấy tài nguyên"))
				.andExpect(jsonPath("$.correlationId").value("request-42"))
				.andExpect(jsonPath("$.detail").value("Resource 42 was not found."))
				.andExpect(jsonPath("$.instance").value("/test/resources/42"));
	}

	@Test
	void returnsUnauthorizedProblemForMissingAuthentication() throws Exception {
		mockMvc.perform(get("/test/authentication-required")
				.header(CorrelationIdFilter.HEADER_NAME, "request-auth"))
				.andExpect(status().isUnauthorized())
				.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.errorCode").value("AUTHENTICATION_REQUIRED"))
				.andExpect(jsonPath("$.title").value("Yêu cầu xác thực"))
				.andExpect(jsonPath("$.correlationId").value("request-auth"));
	}

	@Test
	void returnsFieldErrorsWithoutRejectedValues() throws Exception {
		mockMvc.perform(post("/test/resources")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.title").value("Dữ liệu không hợp lệ"))
				.andExpect(jsonPath("$.errors", hasSize(1)))
				.andExpect(jsonPath("$.errors[0].field").value("name"))
				.andExpect(jsonPath("$.errors[0].message").value("must not be blank"))
				.andExpect(jsonPath("$.errors[0].rejectedValue").doesNotExist());
	}

	@Test
	void hidesUnexpectedExceptionDetails() throws Exception {
		mockMvc.perform(get("/test/unexpected"))
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.errorCode").value("INTERNAL_ERROR"))
				.andExpect(jsonPath("$.title").value("Lỗi hệ thống"))
				.andExpect(jsonPath("$.detail").value("The server could not complete the request."))
				.andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("database-password"))));
	}

	@RestController
	@RequestMapping("/test")
	private static final class TestController {

		@GetMapping("/resources/{id}")
		void getResource(@PathVariable long id) {
			throw new ResourceNotFoundException("Resource " + id + " was not found.");
		}

		@PostMapping("/resources")
		void createResource(@Valid @RequestBody CreateResourceRequest request) {
		}

		@GetMapping("/unexpected")
		void unexpected() {
			throw new IllegalStateException("database-password");
		}

		@GetMapping("/authentication-required")
		void authenticationRequired() {
			throw new AuthenticationRequiredException();
		}
	}

	private record CreateResourceRequest(@NotBlank String name) {
	}
}
