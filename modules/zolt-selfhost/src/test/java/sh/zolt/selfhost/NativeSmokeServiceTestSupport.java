package sh.zolt.selfhost;

import sh.zolt.project.BuildSettings;
import sh.zolt.project.ProjectConfig;
import sh.zolt.project.ProjectConfigs;
import sh.zolt.project.ProjectMetadata;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.io.TempDir;

abstract class NativeSmokeServiceTestSupport {
    @TempDir
    protected Path tempDir;

    protected Path writeBinary() throws IOException {
        Path binary = tempDir.resolve("target/native/zolt");
        Files.createDirectories(binary.getParent());
        Files.writeString(binary, "native");
        return binary;
    }

    protected static ProjectConfig config() {
        return ProjectConfigs.withDirectDependencies(
                new ProjectMetadata("zolt", "0.1.0", "sh.zolt", currentJavaMajorVersion(), Optional.of("sh.zolt.Main")),
                Map.of("central", "https://repo.maven.apache.org/maven2"),
                Map.of(),
                Map.of(),
                BuildSettings.defaults());
    }

    protected static String currentJavaMajorVersion() {
        String version = System.getProperty("java.version");
        String[] parts = version.split("[._+-]", -1);
        if (parts.length >= 2 && "1".equals(parts[0])) {
            return parts[1];
        }
        return parts[0];
    }
}
