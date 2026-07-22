package sh.zolt.cli.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import sh.zolt.error.ActionableException;
import sh.zolt.toolchain.install.ToolchainDownloadMirror;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class CommandNetworkTest {
    private static final URI GITHUB_JDK = URI.create(
            "https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.11%2B10/jdk.tar.gz");

    @Test
    void readsToolchainMirrorFromNetworkConfig(@TempDir Path directory) throws IOException {
        Path configPath = writeConfig(directory, """
                version = 1

                [network]
                toolchainMirror = "https://nexus.example.com/github"
                """);

        ToolchainDownloadMirror mirror = CommandNetwork.toolchainMirror(configPath);

        assertEquals(
                "https://nexus.example.com/github/adoptium/temurin21-binaries/releases/download/"
                        + "jdk-21.0.11%2B10/jdk.tar.gz",
                mirror.rewrite(GITHUB_JDK).toString());
    }

    @Test
    void feedsConfiguredCaBundleToTheTransportAndFailsActionablyWhenMissing(@TempDir Path directory)
            throws IOException {
        Path configPath = writeConfig(directory, """
                version = 1

                [network]
                caBundle = "absent-ca.pem"
                """);

        ActionableException exception = assertThrows(
                ActionableException.class,
                () -> CommandNetwork.transport(configPath));

        assertTrue(exception.getMessage().contains("absent-ca.pem"));
    }

    @Test
    void absentConfigYieldsIdentityMirrorAndUsableTransport(@TempDir Path directory) {
        Path configPath = directory.resolve("missing.toml");

        assertEquals(GITHUB_JDK, CommandNetwork.toolchainMirror(configPath).rewrite(GITHUB_JDK));
        assertNotNull(CommandNetwork.transport(configPath).newHttpClient());
    }

    private static Path writeConfig(Path directory, String content) throws IOException {
        Path configPath = directory.resolve("config.toml");
        Files.writeString(configPath, content);
        return configPath;
    }
}
