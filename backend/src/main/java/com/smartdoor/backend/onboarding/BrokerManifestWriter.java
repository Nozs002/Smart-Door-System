package com.smartdoor.backend.onboarding;

import com.smartdoor.backend.infrastructure.mqtt.DeviceTransportCredentialService;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** Private shared volume, consumed by the Broker supervisor. Atomic complete snapshots. */
@Component @Profile("!provision & !device-security")
@ConditionalOnProperty(name="app.onboarding.broker-manifest-enabled",havingValue="true")
public class BrokerManifestWriter {
    private final DeviceTransportCredentialService credentials;
    private final ObjectMapper json;
    private final Path destination;
    public BrokerManifestWriter(DeviceTransportCredentialService credentials,ObjectMapper json,Environment env) {
        this.credentials=credentials; this.json=json;
        destination=Path.of(env.getProperty("app.onboarding.broker-manifest","/app/mqtt/broker-users.json"));
    }
    @Scheduled(initialDelay=3000,fixedDelay=3000)
    public void write() throws java.io.IOException {
        byte[] value=json.writeValueAsBytes(credentials.exportBrokerUsers().stream()
                .sorted(java.util.Comparator.comparing(DeviceTransportCredentialService.DeviceCredentialExport::deviceId))
                .map(u -> java.util.Map.of("username",u.username(),"password",u.password(),"deviceCode",u.deviceCode(),"doorId",u.doorId()))
                .toList());
        if (Files.exists(destination) && java.util.Arrays.equals(value,Files.readAllBytes(destination))) return;
        Files.createDirectories(destination.getParent());
        Path temporary=Files.createTempFile(destination.getParent(),"broker-users-",".tmp");
        try {
            try { Files.setPosixFilePermissions(temporary,Set.of(PosixFilePermission.OWNER_READ,PosixFilePermission.OWNER_WRITE)); }
            catch (UnsupportedOperationException ignored) { /* Development Windows volume. */ }
            Files.write(temporary,value);
            Files.move(temporary,destination,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temporary); }
    }
}
