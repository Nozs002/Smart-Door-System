package com.smartdoor.backend.onboarding;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartdoor.backend.shared.api.ApiResponseAdvice;
import com.smartdoor.backend.shared.security.CurrentAccountProvider;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ReplacementControllerTests {

	private final ReplacementService service = mock(ReplacementService.class);
	private final CurrentAccountProvider account = mock(CurrentAccountProvider.class);
	private MockMvc mvc;

	@BeforeEach
	void setUp() {
		when(account.requireAccountId()).thenReturn(7L);
		mvc = MockMvcBuilders.standaloneSetup(new ReplacementController(service, account))
				.setControllerAdvice(new ApiResponseAdvice())
				.build();
	}

	@Test
	void returnsExplicitNullDataWhenOwnerHasNoReplacementRequest() throws Exception {
		when(service.current(7L, 18L, 25L)).thenReturn(null);
		mvc.perform(get("/api/v1/doors/18/devices/25/replacement-request").accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andExpect(header().string("Cache-Control", "no-store"))
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.data").hasJsonPath())
				.andExpect(jsonPath("$.data").value(nullValue()));
	}

	@Test
	void returnsExistingReplacementRequestInData() throws Exception {
		when(service.current(7L, 18L, 25L)).thenReturn(Map.of("status", "OPEN"));
		mvc.perform(get("/api/v1/doors/18/devices/25/replacement-request").accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("OPEN"));
	}
}
