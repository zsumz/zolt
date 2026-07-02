package sh.zolt.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class ProjectConfigNormalizerTest {
    @Test
    void defaultRepositorySettingsMirrorDefaultRepositoryUrls() {
        assertEquals(Map.of("central", ProjectConfig.MAVEN_CENTRAL), ProjectConfig.defaultRepositories());
        assertEquals(
                RepositorySettings.unauthenticated("central", ProjectConfig.MAVEN_CENTRAL),
                ProjectConfig.defaultRepositorySettings().get("central"));
    }

    @Test
    void orderedCollectionsPreserveInputOrderAndAreImmutable() {
        Map<String, String> dependencies = new LinkedHashMap<>();
        dependencies.put("com.example:first", "1.0.0");
        dependencies.put("com.example:second", "2.0.0");

        ProjectConfig config = ProjectConfigs.withDirectDependencies(
                new ProjectMetadata("demo", "0.1.0", "com.example", "21", Optional.empty()),
                ProjectConfig.defaultRepositories(),
                dependencies,
                Map.of(),
                BuildSettings.defaults(),
                NativeSettings.defaults());

        assertEquals(List.of("com.example:first", "com.example:second"), List.copyOf(config.dependencies().keySet()));
        assertThrows(UnsupportedOperationException.class, () -> config.dependencies().put("com.example:third", "3.0.0"));
        assertThrows(UnsupportedOperationException.class, () -> config.managedDependencies().add("com.example:managed"));
    }
}
