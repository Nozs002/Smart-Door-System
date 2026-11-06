package com.smartdoor.backend.credentials;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.smartdoor.backend.credentials.entity.Cardholder;
import com.smartdoor.backend.credentials.repository.CardholderRepository;
import com.smartdoor.backend.credentials.repository.RfidCredentialRepository;
import com.smartdoor.backend.credentials.repository.SystemPinRepository;
import com.smartdoor.backend.door.entity.Door;
import com.smartdoor.backend.door.repository.DoorRepository;
import com.smartdoor.backend.identity.dto.LoginRequest;
import com.smartdoor.backend.identity.entity.Account;
import com.smartdoor.backend.identity.repository.AccountRepository;
import com.smartdoor.backend.identity.service.LoginService;
import com.smartdoor.backend.settings.entity.DoorConfig;
import com.smartdoor.backend.settings.repository.DoorConfigRepository;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/** Exercises JWT identity, owner isolation and the credential/settings database transaction together. */
@SpringBootTest
@ActiveProfiles("test")
class CredentialsApiTests {
    @Autowired WebApplicationContext context;
    @Autowired AccountRepository accounts;
    @Autowired CardholderRepository holders;
    @Autowired RfidCredentialRepository rfids;
    @Autowired SystemPinRepository pins;
    @Autowired DoorRepository doors;
    @Autowired DoorConfigRepository configs;
    @Autowired LoginService logins;
    @Autowired PasswordEncoder passwords;
    private MockMvc mvc;
    private Account owner;
    private Account other;
    private Door door;
    private String token;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        owner = account(); other = account();
        door = door(owner.getId(), true);
        token = logins.login(new LoginRequest(owner.getUsername(), "credential-password"))
                .accessToken();
    }

    @Test
    void cardholdersAndDoorCredentialsCannotCrossAccountBoundary() throws Exception {
        Cardholder hidden = holder(other.getId(), "Hidden member");
        Door hiddenDoor = door(other.getId(), true);
        mvc.perform(get("/api/v1/cardholders").header("Authorization", bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(0));
        mvc.perform(patch("/api/v1/cardholders/{id}", hidden.getId()).header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Stolen\",\"status\":\"DISABLED\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/doors/{id}/rfid-credentials", door.getId()).header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cardholderId\":" + hidden.getId() + ",\"uid\":\"AABBCCDD\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/doors/{id}/pin", hiddenDoor.getId()).header("Authorization", bearer()))
                .andExpect(status().isNotFound());
        assertThat(holders.findById(hidden.getId()).orElseThrow().getDisplayName()).isEqualTo("Hidden member");
        assertThat(rfids.findAllByDoorId(door.getId())).isEmpty();
        assertThat(configs.findById(door.getId()).orElseThrow().getCredentialsVersion()).isEqualTo(1);
    }

    @Test
    void issueRevokeAndPinChangesCommitTheirSyncVersionsAndNeverExposeVerifier() throws Exception {
        Cardholder member = holder(owner.getId(), "Member");
        mvc.perform(post("/api/v1/doors/{id}/rfid-credentials", door.getId()).header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cardholderId\":" + member.getId() + ",\"uid\":\"aa-bb-cc-dd\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.uid").value("AABBCCDD"));
        long credential = rfids.findAllByDoorId(door.getId()).getFirst().getId();
        mvc.perform(post("/api/v1/doors/{door}/rfid-credentials/{id}/revoke", door.getId(), credential)
                        .header("Authorization", bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("REVOKED"));
        mvc.perform(post("/api/v1/doors/{door}/rfid-credentials/{id}/revoke", door.getId(), credential)
                        .header("Authorization", bearer()))
                .andExpect(status().isOk());
        String response = mvc.perform(put("/api/v1/doors/{id}/pin", door.getId()).header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"pin\":\"314159\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.configured").value(true))
                .andExpect(jsonPath("$.data.version").value(1)).andReturn().getResponse().getContentAsString();
        String verifier = pins.findById(door.getId()).orElseThrow().getPinHash();
        assertThat(passwords.matches("314159", verifier)).isTrue();
        assertThat(response).doesNotContain("314159", verifier, "pinHash", "pinVerifier");
        assertThat(configs.findById(door.getId()).orElseThrow().getCredentialsVersion()).isEqualTo(4);
    }

    @Test
    void syncFailureRollsBackPinAndInvalidInputDoesNotChangeState() throws Exception {
        Door incomplete = door(owner.getId(), false);
        mvc.perform(put("/api/v1/doors/{id}/pin", incomplete.getId()).header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"pin\":\"1234\"}"))
                .andExpect(status().isNotFound());
        assertThat(pins.findById(incomplete.getId())).isEmpty();
        mvc.perform(put("/api/v1/doors/{id}/pin", door.getId()).header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"pin\":\"12a4\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/cardholders")).andExpect(status().isUnauthorized());
        assertThat(pins.findById(door.getId())).isEmpty();
        assertThat(configs.findById(door.getId()).orElseThrow().getCredentialsVersion()).isEqualTo(1);
    }

    private Account account() {
        Account value = new Account(); value.setUsername("credentials-" + UUID.randomUUID());
        value.setDisplayName("Credential test"); value.setPasswordHash(passwords.encode("credential-password"));
        return accounts.saveAndFlush(value);
    }
    private Door door(Long accountId, boolean configured) {
        Door value = new Door(); value.setOwnerAccountId(accountId); value.setName("Test door");
        value.setStatus(Door.Status.SETUP_PENDING); value = doors.saveAndFlush(value);
        if (configured) { DoorConfig config = new DoorConfig(); config.setDoorId(value.getId()); configs.saveAndFlush(config); }
        return value;
    }
    private Cardholder holder(Long accountId, String name) {
        Cardholder value = new Cardholder(); value.setAccountId(accountId); value.setDisplayName(name);
        return holders.saveAndFlush(value);
    }
    private String bearer() { return "Bearer " + token; }
}
