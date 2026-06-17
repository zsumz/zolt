package com.zolt.build;

import com.zolt.project.BuildMetadataSettings;
import com.zolt.project.BuildSettings;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.io.TempDir;

abstract class ResourceCopierTestSupport {
    @TempDir
    protected Path projectDir;

    protected Path resource(String path, String content) throws IOException {
        Path resource = projectDir.resolve(path);
        Files.createDirectories(resource.getParent());
        Files.writeString(resource, content);
        return resource.normalize();
    }

    protected static BuildSettings buildSettingsWithResourceRoots(
            List<String> resourceRoots,
            List<String> testResourceRoots) {
        return new BuildSettings(
                "src/main/java",
                "src/test/java",
                "target/classes",
                "target/test-classes",
                List.of("src/test/java"),
                resourceRoots,
                testResourceRoots,
                BuildMetadataSettings.defaults());
    }
}
