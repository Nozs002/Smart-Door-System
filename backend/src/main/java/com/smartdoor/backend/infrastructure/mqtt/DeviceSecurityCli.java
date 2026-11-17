package com.smartdoor.backend.infrastructure.mqtt;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** Local technical tool. Credentials are written to a private file, never stdout/logs. */
@Component
@Profile("device-security")
public class DeviceSecurityCli implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(DeviceSecurityCli.class);
    private final DeviceTransportCredentialService credentials;
    private final ObjectMapper json;
    private final ConfigurableApplicationContext context;

    public DeviceSecurityCli(DeviceTransportCredentialService credentials, ObjectMapper json,
            ConfigurableApplicationContext context) {
        this.credentials = credentials; this.json = json; this.context = context;
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        try {
            String action = required(args, "security.action");
            if (!Set.of("issue", "export-broker", "revoke", "confirm-revocation", "check-schema").contains(action)) {
                throw new IllegalArgumentException("Unsupported security.action.");
            }
            Long deviceId = Set.of("export-broker", "check-schema").contains(action) ? null : id(args, "security.device-id");
            String credentialId = action.equals("confirm-revocation") ? required(args, "security.credential-id") : null;
            Path output = Path.of(required(args, "security.output")).toAbsolutePath().normalize();
            if (output.getParent() != null) Files.createDirectories(output.getParent());
            // Fail before rotating a key if the destination exists or cannot be created.
            // The empty file contains no secret while permissions are being restricted.
            Files.createFile(output);
            boolean complete = false;
            try {
                restrictToOwner(output);
                try (OutputStream stream = Files.newOutputStream(output, StandardOpenOption.WRITE)) {
                    Object value = switch (action) {
                        case "issue" -> credentials.issue(deviceId);
                        case "revoke" -> credentials.revoke(deviceId);
                        case "confirm-revocation" -> credentials.confirmBrokerRevocation(deviceId,credentialId);
                        case "check-schema" -> java.util.Map.of("migrationsCompleted",true);
                        default -> credentials.exportBrokerUsers();
                    };
                    stream.write(json.writerWithDefaultPrettyPrinter().writeValueAsBytes(value));
                    stream.flush();
                }
                complete = true;
                log.info("Device security export written to {}", output);
            } finally {
                if (!complete) Files.deleteIfExists(output);
            }
        } finally {
            context.close();
        }
    }

    private void restrictToOwner(Path output) {
        try {
            Files.setPosixFilePermissions(output,
                    Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
            return;
        } catch (UnsupportedOperationException ignored) {
            // Windows uses ACLs rather than POSIX mode bits.
        } catch (IOException | SecurityException exception) {
            log.warn("Could not set POSIX permissions on device security export; trying owner ACL.");
        }
        try {
            AclFileAttributeView acl = Files.getFileAttributeView(output, AclFileAttributeView.class);
            if (acl == null) throw new UnsupportedOperationException("Owner ACL is unavailable");
            AclEntry owner = AclEntry.newBuilder().setType(AclEntryType.ALLOW).setPrincipal(acl.getOwner())
                    .setPermissions(EnumSet.allOf(AclEntryPermission.class)).build();
            acl.setAcl(List.of(owner));
        } catch (IOException | SecurityException | UnsupportedOperationException exception) {
            log.warn("Could not restrict device security export permissions automatically; restrict access to its owner before use.");
        }
    }

    private String required(ApplicationArguments args, String name) {
        List<String> values = args.getOptionValues(name);
        if (values == null || values.size() != 1 || values.getFirst().isBlank()) {
            throw new IllegalArgumentException("Cần đúng một giá trị --" + name);
        }
        return values.getFirst();
    }
    private Long id(ApplicationArguments args, String name) {
        try {
            long value = Long.parseLong(required(args, name));
            if (value <= 0) throw new NumberFormatException();
            return value;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("ID phải là số nguyên dương: " + name);
        }
    }
}
