package com.smartdoor.backend.settings;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartdoor.backend.settings.controller.DoorSettingsController;
import com.smartdoor.backend.settings.dto.DoorConfigView;
import com.smartdoor.backend.settings.service.DoorSettingsService;
import com.smartdoor.backend.shared.api.ApiResponseAdvice;
import com.smartdoor.backend.shared.error.GlobalExceptionHandler;
import com.smartdoor.backend.shared.security.CurrentAccountProvider;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class DoorSettingsControllerTests {
	private final CurrentAccountProvider accounts = mock(CurrentAccountProvider.class);
	private final DoorSettingsService settings = mock(DoorSettingsService.class);
	private MockMvc mvc;

	@BeforeEach
	void setUp() {
		when(accounts.requireAccountId()).thenReturn(7L);
		mvc = MockMvcBuilders.standaloneSetup(new DoorSettingsController(settings, accounts))
				.setControllerAdvice(new ApiResponseAdvice(), new GlobalExceptionHandler()).build();
	}

	@Test
	void returnsConfigurationForAuthenticatedOwner() throws Exception {
		when(settings.get(7L, 3L)).thenReturn(new DoorConfigView(3L, 3, 60,
				new BigDecimal("50.00"), null, 2L, 4L, null, List.of()));
		mvc.perform(get("/api/v1/doors/3/configuration"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.version").value(2))
				.andExpect(jsonPath("$.data.credentialsVersion").value(4));
		verify(settings).get(7L, 3L);
	}

	@Test
	void rejectsOutOfRangeConfigurationBeforeMutation() throws Exception {
		mvc.perform(put("/api/v1/doors/3/configuration")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"maxFailedAttempts\":0,\"lockoutSeconds\":2,\"temperatureThreshold\":100}"))
				.andExpect(status().isBadRequest());
		verify(settings, never()).update(any(), any(), any());
	}
}
