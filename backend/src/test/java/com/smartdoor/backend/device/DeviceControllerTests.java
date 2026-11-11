package com.smartdoor.backend.device;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartdoor.backend.device.controller.DeviceController;
import com.smartdoor.backend.device.dto.DeviceView;
import com.smartdoor.backend.device.dto.LifecycleRequest.Action;
import com.smartdoor.backend.device.entity.Device;
import com.smartdoor.backend.device.service.DeviceLifecycleService;
import com.smartdoor.backend.device.service.DeviceQueryService;
import com.smartdoor.backend.shared.api.ApiResponseAdvice;
import com.smartdoor.backend.shared.api.PageResponse;
import com.smartdoor.backend.shared.error.AuthenticationRequiredException;
import com.smartdoor.backend.shared.error.ConflictException;
import com.smartdoor.backend.shared.error.GlobalExceptionHandler;
import com.smartdoor.backend.shared.error.ResourceNotFoundException;
import com.smartdoor.backend.shared.security.CurrentAccountProvider;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class DeviceControllerTests {
	private final CurrentAccountProvider accounts = mock(CurrentAccountProvider.class);
	private final DeviceQueryService queries = mock(DeviceQueryService.class);
	private final DeviceLifecycleService lifecycle = mock(DeviceLifecycleService.class);
	private MockMvc mvc;

	@BeforeEach
	void setUp() {
		when(accounts.requireAccountId()).thenReturn(1L);
		mvc = MockMvcBuilders.standaloneSetup(new DeviceController(accounts, queries, lifecycle))
				.setControllerAdvice(new ApiResponseAdvice(), new GlobalExceptionHandler()).build();
	}

	@Test
	void returnsWrappedDevicesWithoutAcceptingAccountFromRequest() throws Exception {
		DeviceView view = new DeviceView(11L, 10L, "n1", Device.NodeType.NODE_1,
				Device.LifecycleStatus.PROVISIONED, DeviceView.ConnectionStatus.UNKNOWN,
				null, null, null, null);
		when(queries.listDevices(1L, 10L)).thenReturn(List.of(view));
		mvc.perform(get("/api/v1/doors/10/devices").param("accountId", "999"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data[0].id").value(11))
				.andExpect(jsonPath("$.data[0].connectionStatus").value("UNKNOWN"));
		verify(queries).listDevices(1L, 10L);
	}

	@Test
	void missingAuthenticatedAccountReturns401AndDoesNotQuery() throws Exception {
		when(accounts.requireAccountId()).thenThrow(new AuthenticationRequiredException());
		mvc.perform(get("/api/v1/doors/10/devices")).andExpect(status().isUnauthorized());
		verify(queries, never()).listDevices(any(), any());
	}

	@Test
	void foreignDoorUses404WithoutExposingDevices() throws Exception {
		when(queries.listDevices(1L, 10L)).thenThrow(new ResourceNotFoundException("Door not found"));
		mvc.perform(get("/api/v1/doors/10/devices")).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
	}

	@Test
	void lifecycleUsesAuthenticatedActorAndReturnsConflictWhenNotReady() throws Exception {
		when(lifecycle.changeLifecycle(1L, 10L, 11L, Action.ACTIVATE, null))
				.thenThrow(new ConflictException("Not ready"));
		mvc.perform(post("/api/v1/doors/10/devices/11/lifecycle").param("accountId", "999")
				.contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"ACTIVATE\"}"))
				.andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("CONFLICT"));
		verify(lifecycle).changeLifecycle(1L, 10L, 11L, Action.ACTIVATE, null);
	}

	@Test
	void invalidActionOrMissingActionReturns400BeforeMutation() throws Exception {
		mvc.perform(post("/api/v1/doors/10/devices/11/lifecycle").contentType(MediaType.APPLICATION_JSON)
				.content("{\"action\":\"UNLOCK\"}")).andExpect(status().isBadRequest());
		mvc.perform(post("/api/v1/doors/10/devices/11/lifecycle").contentType(MediaType.APPLICATION_JSON)
				.content("{}")).andExpect(status().isBadRequest());
		verify(lifecycle, never()).changeLifecycle(any(), any(), any(), any(), any());
	}

	@Test
	void replacementRequiresReasonAndPositiveRegisteredDeviceId() throws Exception {
		mvc.perform(post("/api/v1/doors/10/devices/11/replace").contentType(MediaType.APPLICATION_JSON)
				.content("{\"replacementDeviceId\":-1,\"reason\":\"\"}"))
				.andExpect(status().isBadRequest());
		verify(lifecycle, never()).replaceDevice(any(), any(), any(), any(), any());
	}

	@Test
	void historyDefaultsToBoundedPageAndSupportsPerDeviceFilter() throws Exception {
		when(queries.history(eq(1L), eq(10L), eq(11L), eq(0), eq(20)))
				.thenReturn(new PageResponse<>(List.of(), 0, 20, 0, 0));
		mvc.perform(get("/api/v1/doors/10/lifecycle-events").param("deviceId", "11"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.page").value(0))
				.andExpect(jsonPath("$.data.size").value(20));
	}

	@Test
	void heartbeatIsNotAPublicMutationRoute() throws Exception {
		mvc.perform(post("/api/v1/doors/10/devices/11/heartbeat").contentType(MediaType.APPLICATION_JSON)
				.content("{\"appliedPinVersion\":999}"))
				.andExpect(status().isNotFound());
	}
}
