package com.smartdoor.backend.onboarding;

import com.smartdoor.backend.infrastructure.mqtt.DeviceTransportCredentialService;
import java.nio.file.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** Only the Broker writes this private volume; no client HTTP endpoint can assert proof. */
@Component @Profile("!provision & !device-security")
@ConditionalOnProperty(name="app.onboarding.broker-manifest-enabled",havingValue="true")
public class BrokerRevocationProofReader {
    private static final Logger log=LoggerFactory.getLogger(BrokerRevocationProofReader.class);
    private final DeviceTransportCredentialService credentials;
    private final ObjectMapper json;
    private final Path path;
    public BrokerRevocationProofReader(DeviceTransportCredentialService credentials,ObjectMapper json,Environment env) {
        this.credentials=credentials; this.json=json;
        path=Path.of(env.getProperty("app.onboarding.broker-proof","/app/broker-proof/revocations.json"));
    }
    @Scheduled(initialDelay=2000,fixedDelay=2000)
    public void read() {
        if (!Files.isRegularFile(path)) return;
        try {
            if (Files.size(path)>1048576) throw new IllegalArgumentException("Oversized proof");
            var records=json.readTree(Files.readAllBytes(path));
            if (!records.isArray()) throw new IllegalArgumentException("Invalid proof shape");
            for (var record:records) {
                if (!record.path("deviceId").isIntegralNumber() || !record.path("credentialId").isTextual()
                        || !record.path("revokedAt").isTextual() || !record.path("sessionsDisconnected").asBoolean(false)
                        || !record.path("authenticationRejected").asBoolean(false)) continue;
                credentials.importBrokerProof(record.path("deviceId").asLong(),record.path("credentialId").asText(),record.path("revokedAt").asText());
            }
        } catch (Exception invalid) {
            log.warn("Broker revocation proof unavailable or invalid; replacement remains pending.");
        }
    }
}
