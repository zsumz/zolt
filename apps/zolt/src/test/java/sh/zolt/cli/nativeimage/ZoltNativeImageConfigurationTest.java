package sh.zolt.cli.nativeimage;

import static org.junit.jupiter.api.Assertions.assertTrue;

import sh.zolt.project.ProjectConfig;
import sh.zolt.toml.ZoltTomlParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class ZoltNativeImageConfigurationTest {
    @Test
    void nativeZoltEnablesHttpsForReleaseUpdateDownloads() throws IOException {
        ProjectConfig config = new ZoltTomlParser().parse(Files.readString(zoltAppConfigPath()));

        assertTrue(
                config.nativeSettings().args().contains("--enable-url-protocols=https"),
                "native zolt must keep HTTPS URL protocol support for release channel and archive downloads");
    }

    private static Path zoltAppConfigPath() {
        Path workspacePath = Path.of("apps/zolt/zolt.toml");
        if (Files.exists(workspacePath)) {
            return workspacePath;
        }
        return Path.of("zolt.toml");
    }
}
