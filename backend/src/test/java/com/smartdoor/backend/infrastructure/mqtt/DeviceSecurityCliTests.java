package com.smartdoor.backend.infrastructure.mqtt;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.smartdoor.backend.infrastructure.mqtt.DeviceTransportCredentialService.DeviceCredentialExport;
import com.smartdoor.backend.shared.error.BadRequestException;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermission;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.context.ConfigurableApplicationContext;
import tools.jackson.databind.ObjectMapper;

class DeviceSecurityCliTests {
    @TempDir Path directory;
    private final DeviceTransportCredentialService credentials = mock(DeviceTransportCredentialService.class);
    private final ConfigurableApplicationContext context = mock(ConfigurableApplicationContext.class);
    private final ObjectMapper json = new ObjectMapper();
    private final DeviceSecurityCli cli = new DeviceSecurityCli(credentials, json, context);
    private final DeviceCredentialExport value = new DeviceCredentialExport(11L, 5L, "NODE-1", "NODE_1",
            "credential-id", "device-11-credential-id", "private-broker-password", "private-signing-key");

    @Test
    void issueCreatesParentAndWritesPrivateJsonWithoutOverwritingAnotherExport() throws Exception {
        Path output = directory.resolve("private/node-11.json");
        when(credentials.issue(11L)).thenReturn(value);
        cli.run(new DefaultApplicationArguments("--security.action=issue", "--security.device-id=11", "--security.output="+output));
        var document = json.readTree(Files.readString(output));
        assertThat(document.get("deviceId").asLong()).isEqualTo(11L);
        assertThat(document.get("password").asText()).isEqualTo("private-broker-password");
        assertThat(document.get("signingKey").asText()).isEqualTo("private-signing-key");
        if (Files.getFileStore(output).supportsFileAttributeView("posix")) {
            assertThat(Files.getPosixFilePermissions(output)).containsExactlyInAnyOrder(
                    PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
        }
        verify(credentials).issue(11L);
        verify(context).close();
    }

    @Test
    void brokerExportIsAnArrayOfCurrentDeviceCredentials() throws Exception {
        Path output = directory.resolve("broker-users.json");
        when(credentials.exportBrokerUsers()).thenReturn(List.of(value));
        cli.run(new DefaultApplicationArguments("--security.action=export-broker", "--security.output="+output));
        var document = json.readTree(Files.readString(output));
        assertThat(document.isArray()).isTrue();
        assertThat(document.size()).isEqualTo(1);
        assertThat(document.get(0).get("username").asText()).isEqualTo("device-11-credential-id");
        verify(credentials, never()).issue(any());
        verify(context).close();
    }

    @Test
    void existingFileIsRejectedBeforeRotatingTheDatabaseCredential() throws Exception {
        Path output = directory.resolve("existing.json"); Files.writeString(output, "preserve-existing-export");
        assertThatThrownBy(() -> cli.run(new DefaultApplicationArguments("--security.action=issue",
                "--security.device-id=11", "--security.output="+output))).isInstanceOf(FileAlreadyExistsException.class);
        assertThat(Files.readString(output)).isEqualTo("preserve-existing-export");
        verifyNoInteractions(credentials);
        verify(context).close();
    }

    @Test
    void invalidActionOrDeviceIdDoesNotIssueOrCreateAFile() {
        Path output = directory.resolve("invalid.json");
        for (String[] arguments : List.of(
                new String[]{"--security.action=delete", "--security.output="+output},
                new String[]{"--security.action=issue", "--security.device-id=0", "--security.output="+output},
                new String[]{"--security.action=issue", "--security.device-id=11", "--security.device-id=12", "--security.output="+output},
                new String[]{"--security.action=issue", "--security.device-id=11"})) {
            assertThatThrownBy(() -> cli.run(new DefaultApplicationArguments(arguments))).isInstanceOf(IllegalArgumentException.class);
        }
        assertThat(output).doesNotExist();
        verifyNoInteractions(credentials);
        verify(context, times(4)).close();
    }

    @Test
    void failedCredentialIssuanceRemovesItsIncompleteFileAndClosesContext() {
        Path output = directory.resolve("failed.json");
        when(credentials.issue(11L)).thenThrow(new BadRequestException("Missing device master key"));
        assertThatThrownBy(() -> cli.run(new DefaultApplicationArguments("--security.action=issue",
                "--security.device-id=11", "--security.output="+output))).isInstanceOf(BadRequestException.class);
        assertThat(output).doesNotExist();
        verify(context).close();
    }
}
