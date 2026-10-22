package com.smartdoor.backend.door;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.smartdoor.backend.door.service.DoorProvisioningService;
import com.smartdoor.backend.door.service.DoorService;
import com.smartdoor.backend.identity.entity.Account;
import com.smartdoor.backend.identity.repository.AccountRepository;
import com.smartdoor.backend.shared.security.AccountPrincipal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

/** Auth giả chỉ gắn vào request test; chạy qua SecurityFilterChain thật của ứng dụng. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DoorSecurityIntegrationTests {
	@Autowired WebApplicationContext context;
	@Autowired AccountRepository accounts;
	@Autowired DoorProvisioningService provisioning;
	@Autowired DoorService service;
	private MockMvc mvc;

	@BeforeEach
	void setup() {
		mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
	}

	@Test
	void missingOrSelfAssertedBearerNeverGrantsDoorAccess() throws Exception {
		mvc.perform(get("/api/v1/doors")).andExpect(status().isUnauthorized());
		mvc.perform(get("/api/v1/doors").header("Authorization", "Bearer arbitrary-client-value")
				.header("X-Account-Id", "1")).andExpect(status().isUnauthorized());
	}

	@Test
	void authenticatedAccountOnlySeesOwnDoorAndDisabledAccountIsRejected() throws Exception {
		Account owner = account();
		Account other = account();
		String suffix = UUID.randomUUID().toString();
		var result = provisioning.provision("Private door", null, "S1-" + suffix, "S2-" + suffix);
		service.claimDoor(owner.getId(), result.activationCode());
		var ownerAuth = authenticated(owner.getId());
		mvc.perform(get("/api/v1/doors/" + result.doorId()).with(authentication(ownerAuth)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.name").value("Private door"));
		mvc.perform(get("/api/v1/doors/" + result.doorId()).with(authentication(authenticated(other.getId()))))
				.andExpect(status().isNotFound());
		owner.setStatus(Account.Status.DISABLED);
		accounts.saveAndFlush(owner);
		mvc.perform(get("/api/v1/doors").with(authentication(ownerAuth))).andExpect(status().isUnauthorized());
	}

	private UsernamePasswordAuthenticationToken authenticated(Long id) {
		AccountPrincipal principal = () -> id;
		return new UsernamePasswordAuthenticationToken(principal, null, List.of());
	}
	private Account account() {
		Account account = new Account();
		account.setUsername(UUID.randomUUID().toString());
		account.setDisplayName("Security test");
		account.setPasswordHash("test-placeholder");
		return accounts.saveAndFlush(account);
	}
}
