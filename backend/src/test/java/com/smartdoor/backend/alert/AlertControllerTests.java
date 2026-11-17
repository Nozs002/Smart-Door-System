package com.smartdoor.backend.alert;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartdoor.backend.alert.controller.AlertController;
import com.smartdoor.backend.alert.dto.AlertView;
import com.smartdoor.backend.alert.entity.Alert;
import com.smartdoor.backend.alert.service.AlertService;
import com.smartdoor.backend.shared.api.ApiResponseAdvice;
import com.smartdoor.backend.shared.api.PageResponse;
import com.smartdoor.backend.shared.error.GlobalExceptionHandler;
import com.smartdoor.backend.shared.security.CurrentAccountProvider;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AlertControllerTests {
	private final CurrentAccountProvider accounts = mock(CurrentAccountProvider.class);
	private final AlertService alerts = mock(AlertService.class);
	private MockMvc mvc;

	@BeforeEach
	void setUp() {
		when(accounts.requireAccountId()).thenReturn(7L);
		mvc = MockMvcBuilders.standaloneSetup(new AlertController(alerts, accounts))
				.setControllerAdvice(new ApiResponseAdvice(), new GlobalExceptionHandler()).build();
	}

	@Test
	void listsOnlyThroughAuthenticatedAccount() throws Exception {
		when(alerts.list(7L, 3L, Alert.Status.ACTIVE, 0, 20))
				.thenReturn(new PageResponse<>(List.of(view(Alert.Status.ACTIVE)), 0, 20, 1, 1));
		mvc.perform(get("/api/v1/alerts").param("doorId", "3").param("status", "ACTIVE"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items[0].doorName").value("Cửa chính"));
		verify(alerts).list(7L, 3L, Alert.Status.ACTIVE, 0, 20);
	}

	@Test
	void resolvesAlertUsingServerSideOwner() throws Exception {
		when(alerts.changeStatus(7L, 9L, Alert.Status.RESOLVED)).thenReturn(view(Alert.Status.RESOLVED));
		mvc.perform(patch("/api/v1/alerts/9/status").contentType(MediaType.APPLICATION_JSON)
				.content("{\"status\":\"RESOLVED\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("RESOLVED"));
		verify(alerts).changeStatus(7L, 9L, Alert.Status.RESOLVED);
	}

	private AlertView view(Alert.Status status) {
		return new AlertView(9L, "event-9", 3L, "Cửa chính", 12L,
				Alert.Type.HIGH_TEMPERATURE, Alert.Severity.CRITICAL, status,
				new java.math.BigDecimal("55.5"), LocalDateTime.of(2026, 10, 27, 2, 31),
				status == Alert.Status.RESOLVED ? LocalDateTime.of(2026, 10, 27, 2, 40) : null);
	}
}
