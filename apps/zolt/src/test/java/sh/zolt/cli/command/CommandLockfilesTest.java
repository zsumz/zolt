package sh.zolt.cli.command;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import sh.zolt.project.ProjectConfig;
import sh.zolt.resolve.fingerprint.ProjectResolutionFingerprint;
import sh.zolt.toml.ZoltTomlParser;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class CommandLockfilesTest {
    @TempDir
    Path tempDir;

    @Test
    void acceptsMatchingProjectResolutionFingerprintWithoutResolvingGraph() throws Exception {
        ProjectConfig config = config("1.0.0");
        Path lockfile = tempDir.resolve("zolt.lock");
        Files.writeString(lockfile, """
                version = 1
                projectResolutionFingerprint = "%s"

                [[package]]
                id = "com.example:demo"
                """.formatted(ProjectResolutionFingerprint.fingerprint(config)));

        assertTrue(CommandLockfiles.matchesProjectResolutionFingerprint(lockfile, config));
        assertFalse(CommandLockfiles.matchesProjectResolutionFingerprint(lockfile, config("2.0.0")));
    }

    @Test
    void requiresFullVerificationWhenFingerprintIsMissing() throws Exception {
        Path lockfile = tempDir.resolve("zolt.lock");
        Files.writeString(lockfile, "version = 1\n");

        assertFalse(CommandLockfiles.matchesProjectResolutionFingerprint(lockfile, config("1.0.0")));
    }

    private ProjectConfig config(String dependencyVersion) throws Exception {
        Path project = tempDir.resolve("project-" + dependencyVersion);
        Files.createDirectories(project);
        Files.writeString(project.resolve("zolt.toml"), """
                [project]
                name = "demo"
                version = "0.1.0"
                group = "com.example"
                java = "21"

                [dependencies]
                "com.example:demo" = "%s"
                """.formatted(dependencyVersion));
        return new ZoltTomlParser().parse(project.resolve("zolt.toml"));
    }
}
