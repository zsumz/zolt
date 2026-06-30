package com.zolt.toml.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.project.BuildMetadataSettings;
import com.zolt.project.BuildSettings;
import com.zolt.project.DependencySection;
import com.zolt.project.ProjectConfig;
import com.zolt.project.ResourceFilteringSettings;
import com.zolt.project.ResourceMissingTokenPolicy;
import com.zolt.project.ResourceTokenSettings;
import com.zolt.toml.ZoltTomlParser;
import com.zolt.toml.ZoltTomlWriter;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class ZoltTomlResourcesWriterTest {
    private final ZoltTomlParser parser = new ZoltTomlParser();
    private final ZoltTomlWriter writer = new ZoltTomlWriter();

    @Test
    void writesResourceRootsWhenConfigured() {
        ProjectConfig original = writer.defaultApplicationConfig("hello", "com.example", "com.example.Main")
                .withBuildSettings(new BuildSettings(
                        "src/main/java",
                        "src/test/java",
                        "target/classes",
                        "target/test-classes",
                        List.of("src/test/java"),
                        List.of("src/main/resources", "target/generated/resources"),
                        List.of("src/test/resources", "target/generated/test-resources"),
                        BuildMetadataSettings.defaults()));

        String toml = writer.write(original);
        ProjectConfig parsed = parser.parse(toml);

        assertTrue(toml.contains("[resources]\n"));
        assertTrue(toml.contains("main = [\"src/main/resources\", \"target/generated/resources\"]"));
        assertTrue(toml.contains("test = [\"src/test/resources\", \"target/generated/test-resources\"]"));
        assertEquals(original.build().resourceRoots(), parsed.build().resourceRoots());
        assertEquals(original.build().testResourceRoots(), parsed.build().testResourceRoots());
    }

    @Test
    void writesResourceFilteringWhenConfigured() {
        ProjectConfig original = writer.defaultApplicationConfig("hello", "com.example", "com.example.Main")
                .withBuildSettings(BuildSettings.defaults().withResourceFiltering(resourceFilteringSettings()));

        String toml = writer.write(original);
        ProjectConfig parsed = parser.parse(toml);

        assertTrue(toml.contains("[resources.filtering]\n"));
        assertTrue(toml.contains("enabled = true"));
        assertTrue(toml.contains("test = true"));
        assertTrue(toml.contains("includes = [\"**/*.properties\", \"**/*.yml\"]"));
        assertTrue(toml.contains("missing = \"keep\""));
        assertTrue(toml.contains("[resources.tokens]\n"));
        assertTrue(toml.contains("\"literalName\" = { value = \"demo-app\" }"));
        assertTrue(toml.contains("\"projectVersion\" = { project = \"version\" }"));
        assertTrue(toml.contains("\"ciBuild\" = { env = \"CI_BUILD_NUMBER\" }"));
        assertEquals(original.build().resourceFiltering(), parsed.build().resourceFiltering());
    }

    @Test
    void preservesResourceRootsWhenEditingDependencies() {
        ProjectConfig config = writer.defaultApplicationConfig("hello", "com.example", "com.example.Main")
                .withBuildSettings(new BuildSettings(
                        "src/main/java",
                        "src/test/java",
                        "target/classes",
                        "target/test-classes",
                        List.of("src/test/java"),
                        List.of("src/main/resources", "target/generated/resources"),
                        List.of("src/test/resources"),
                        BuildMetadataSettings.defaults()));
        config = writer.addDependency(config, DependencySection.MAIN, "com.example:app", "1.0.0");
        config = writer.addManagedDependency(config, DependencySection.TEST, "com.example:test-helper");

        ProjectConfig parsed = parser.parse(writer.write(config));

        assertEquals(config.build().resourceRoots(), parsed.build().resourceRoots());
        assertEquals(config.build().testResourceRoots(), parsed.build().testResourceRoots());
    }

    @Test
    void preservesResourceFilteringWhenEditingDependencies() {
        ProjectConfig config = writer.defaultApplicationConfig("hello", "com.example", "com.example.Main")
                .withBuildSettings(BuildSettings.defaults().withResourceFiltering(resourceFilteringSettings()));
        config = writer.addDependency(config, DependencySection.MAIN, "com.example:app", "1.0.0");
        config = writer.addManagedDependency(config, DependencySection.TEST, "com.example:test-helper");

        ProjectConfig parsed = parser.parse(writer.write(config));

        assertEquals(config.build().resourceFiltering(), parsed.build().resourceFiltering());
    }

    private static ResourceFilteringSettings resourceFilteringSettings() {
        return new ResourceFilteringSettings(
                true,
                true,
                List.of("**/*.properties", "**/*.yml"),
                ResourceMissingTokenPolicy.KEEP,
                Map.of(
                        "projectVersion", ResourceTokenSettings.project("version"),
                        "literalName", ResourceTokenSettings.literal("demo-app"),
                        "ciBuild", ResourceTokenSettings.env("CI_BUILD_NUMBER")));
    }
}
