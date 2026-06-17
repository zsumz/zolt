package com.zolt.build;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import com.zolt.project.BuildSettings;
import com.zolt.project.FrameworkSettings;
import com.zolt.project.ProjectConfig;
import com.zolt.project.ProjectConfigs;
import com.zolt.project.ProjectMetadata;
import com.zolt.project.QuarkusPackageMode;
import com.zolt.project.QuarkusSettings;
import org.junit.jupiter.api.io.TempDir;

abstract class CleanServiceTestSupport {
    protected final CleanService cleanService = new CleanService();

    @TempDir
    protected Path projectDir;

    protected void file(String path) throws IOException {
        Path file = projectDir.resolve(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, "x");
    }

    protected static void createSymlink(Path link, Path target) throws IOException {
        Files.createDirectories(link.getParent());
        try {
            Files.createSymbolicLink(link, target);
        } catch (UnsupportedOperationException | IOException exception) {
            assumeTrue(false, "symbolic links are unavailable: " + exception.getMessage());
        }
    }

    protected static ProjectConfig config(BuildSettings buildSettings, boolean quarkusEnabled) {
        return ProjectConfigs.withDirectDependencies(
                new ProjectMetadata("demo", "0.1.0", "com.example", "21", Optional.empty()),
                Map.of(),
                Map.of(),
                Map.of(),
                buildSettings)
                .withFrameworkSettings(new FrameworkSettings(
                        new QuarkusSettings(quarkusEnabled, QuarkusPackageMode.FAST_JAR)));
    }
}
