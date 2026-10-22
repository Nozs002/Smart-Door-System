package com.smartdoor.backend.shared.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.smartdoor.backend.shared.observability.CorrelationIdFilter;

class ApiResponseAdviceTests {

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
				.setControllerAdvice(new ApiResponseAdvice())
				.addFilters(new CorrelationIdFilter())
				.build();
	}

	@Test
	void wrapsJsonResponseWithDefaultMessage() throws Exception {
		mockMvc.perform(get("/test-api/default")
				.header(CorrelationIdFilter.HEADER_NAME, "request-123"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.message").value("Thành công"))
				.andExpect(jsonPath("$.data.id").value(1))
				.andExpect(jsonPath("$.timestamp").exists())
				.andExpect(jsonPath("$.correlationId").value("request-123"));
	}

	@Test
	void keepsCustomMessageAndAddsCorrelationId() throws Exception {
		mockMvc.perform(get("/test-api/custom")
				.header(CorrelationIdFilter.HEADER_NAME, "request-456"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.message").value("Lấy thông tin cửa thành công"))
				.andExpect(jsonPath("$.data.id").value(1))
				.andExpect(jsonPath("$.correlationId").value("request-456"));
	}

	@RestController
	@RequestMapping("/test-api")
	private static final class TestController {

		@GetMapping("/default")
		Map<String, Object> defaultResponse() {
			return Map.of("id", 1);
		}

		@GetMapping("/custom")
		ApiResponse<Map<String, Object>> customResponse() {
			return ApiResponse.success("Lấy thông tin cửa thành công", Map.of("id", 1));
		}
	}
}
