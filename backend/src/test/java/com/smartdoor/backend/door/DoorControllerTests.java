package com.smartdoor.backend.door;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.smartdoor.backend.door.controller.DoorController;
import com.smartdoor.backend.door.dto.DoorView;
import com.smartdoor.backend.door.entity.Door;
import com.smartdoor.backend.door.service.ClaimRateLimiter;
import com.smartdoor.backend.door.service.DoorService;
import com.smartdoor.backend.shared.api.ApiResponseAdvice;
import com.smartdoor.backend.shared.error.AuthenticationRequiredException;
import com.smartdoor.backend.shared.error.GlobalExceptionHandler;
import com.smartdoor.backend.shared.security.CurrentAccountProvider;
import java.time.Clock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class DoorControllerTests {
	private final DoorService service = mock(DoorService.class);
	private final CurrentAccountProvider current = mock(CurrentAccountProvider.class);
	private MockMvc mvc;

	@BeforeEach
	void setup() {
		mvc = MockMvcBuilders.standaloneSetup(new DoorController(service, current, new ClaimRateLimiter(Clock.systemUTC())))
				.setControllerAdvice(new GlobalExceptionHandler(), new ApiResponseAdvice()).build();
	}

	@Test
	void claimUsesAuthenticatedAccountAndNeverAnAccountFromBody() throws Exception {
		when(current.requireAccountId()).thenReturn(7L);
		when(service.claimDoor(7L, "ABCD-2345")).thenReturn(new DoorView(3L, "Door", null, Door.Status.SETUP_PENDING, null, null));
		mvc.perform(post("/api/v1/doors/claim").contentType(MediaType.APPLICATION_JSON)
				.content("{\"activationCode\":\"ABCD-2345\"}"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(3));
		verify(service).claimDoor(7L, "ABCD-2345");
	}

	@Test
	void missingSessionReturns401AndInvalidBodyReturns400() throws Exception {
		when(current.requireAccountId()).thenThrow(new AuthenticationRequiredException());
		mvc.perform(get("/api/v1/doors")).andExpect(status().isUnauthorized());
		mvc.perform(post("/api/v1/doors/claim").contentType(MediaType.APPLICATION_JSON).content("{\"activationCode\":\"\"}"))
				.andExpect(status().isBadRequest());
		verifyNoInteractions(service);
	}
}
