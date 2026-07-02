package sh.zolt.release.archive;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import sh.zolt.project.BuildSettings;
import sh.zolt.project.NativeSettings;
import sh.zolt.project.ProjectConfig;
import sh.zolt.project.ProjectConfigs;
import sh.zolt.project.ProjectMetadata;
import sh.zolt.release.ReleaseTarget;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ReleaseArchiveFailureTest {
    @TempDir
    private Path projectDir;

    private final ReleaseArchiveService service = new ReleaseArchiveService();

    @Test
    void missingBinaryFailsWithNextStep() {
        ReleaseArchiveException exception = assertThrows(
                ReleaseArchiveException.class,
                () -> service.assemble(
                        projectDir,
                        config(),
                        ReleaseTarget.LINUX_X64,
                        Path.of("target/native/zolt"),
                        Path.of("dist")));

        assertTrue(exception.getMessage().contains("Release archive requires native binary"));
        assertTrue(exception.getMessage().contains("Run `zolt native` or pass --binary <path>"));
    }

    @Test
    void rejectsArchiveNameThatUsesUnsafeProjectVersion() throws IOException {
        writeProjectFiles();
        Path binary = writeBinary("target/native/zolt");

        ReleaseArchiveException exception = assertThrows(
                ReleaseArchiveException.class,
                () -> service.assemble(
                        projectDir,
                        config(new ProjectMetadata(
                                "zolt",
                                "../0.1.0",
                                "sh.zolt",
                                currentJavaMajorVersion(),
                                Optional.of("sh.zolt.Main"))),
                        ReleaseTarget.LINUX_X64,
                        binary,
                        Path.of("dist")));

        assertTrue(exception.getMessage().contains("[project].version"));
        assertTrue(exception.getMessage().contains("../0.1.0"));
        assertFalse(Files.exists(projectDir.resolve("dist/zolt-0.1.0-linux-x64.tar.gz")));
    }

    @Test
    void rejectsAbsoluteOutputOutsideProject() throws IOException {
        writeProjectFiles();
        Path binary = writeBinary("target/native/zolt");

        ReleaseArchiveException exception = assertThrows(
                ReleaseArchiveException.class,
                () -> service.assemble(
                        projectDir,
                        config(),
                        ReleaseTarget.LINUX_X64,
                        binary,
                        Path.of("/tmp/release-outside-project")));

        assertTrue(exception.getMessage().contains("Invalid --output path"));
        assertTrue(exception.getMessage().contains("Use a project-relative path or an absolute path under"));
    }

    private void writeProjectFiles() throws IOException {
        Files.writeString(projectDir.resolve("README.md"), "# Demo\n");
        Files.writeString(projectDir.resolve("LICENSE"), "license\n");
    }

    private Path writeBinary(String path) throws IOException {
        Path binary = projectDir.resolve(path);
        Files.createDirectories(binary.getParent());
        Files.writeString(binary, "native");
        return Path.of(path);
    }

    private static ProjectConfig config() {
        return config(new ProjectMetadata(
                "zolt",
                "0.1.0",
                "sh.zolt",
                currentJavaMajorVersion(),
                Optional.of("sh.zolt.Main")));
    }

    private static ProjectConfig config(ProjectMetadata projectMetadata) {
        return ProjectConfigs.withDirectDependencies(
                projectMetadata,
                Map.of("central", "https://repo.maven.apache.org/maven2"),
                Map.of(),
                Map.of(),
                BuildSettings.defaults(),
                new NativeSettings("zolt", "target/native", List.of("--no-fallback")));
    }

    private static String currentJavaMajorVersion() {
        String version = System.getProperty("java.version");
        String[] parts = version.split("[._+-]", -1);
        if (parts.length >= 2 && "1".equals(parts[0])) {
            return parts[1];
        }
        return parts[0];
    }
}
