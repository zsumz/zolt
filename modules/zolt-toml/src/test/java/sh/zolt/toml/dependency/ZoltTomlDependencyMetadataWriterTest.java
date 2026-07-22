package sh.zolt.toml.dependency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import sh.zolt.project.DependencyExclusionSpec;
import sh.zolt.project.DependencyMetadata;
import sh.zolt.project.DependencySection;
import sh.zolt.project.ProjectConfig;
import sh.zolt.toml.ZoltTomlParser;
import sh.zolt.toml.ZoltTomlWriter;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class ZoltTomlDependencyMetadataWriterTest {
    private final ZoltTomlParser parser = new ZoltTomlParser();
    private final ZoltTomlWriter writer = new ZoltTomlWriter();

    @Test
    void writesDependencyMetadataWhenConfigured() {
        ProjectConfig config = writer.defaultApplicationConfig("hello", "com.example", "com.example.Main")
                .withDependencyMetadata(Map.of(
                        DependencyMetadata.key("dependencies", "com.example:core"),
                        new DependencyMetadata(
                                "dependencies",
                                "com.example:core",
                                "1.0.0",
                                false,
                                null,
                                true,
                                false,
                                List.of(new DependencyExclusionSpec("com.example", "legacy-logging"))),
                        DependencyMetadata.key("dependencies", "com.example:publish-helper"),
                        new DependencyMetadata(
                                "dependencies",
                                "com.example:publish-helper",
                                "2.0.0",
                                false,
                                null,
                                false,
                                true,
                                List.of())));
        config = writer.addDependency(config, DependencySection.MAIN, "com.example:core", "1.0.0");

        String toml = writer.write(config);
        ProjectConfig parsed = parser.parse(toml);

        assertTrue(toml.contains("\"com.example:core\" = { version = \"1.0.0\", optional = true, exclusions = [{ group = \"com.example\", artifact = \"legacy-logging\" }] }"));
        assertTrue(toml.contains("\"com.example:publish-helper\" = { version = \"2.0.0\", publishOnly = true }"));
        assertEquals(config.dependencyMetadata(), parsed.dependencyMetadata());
        assertEquals("1.0.0", parsed.dependencies().get("com.example:core"));
        assertFalse(parsed.dependencies().containsKey("com.example:publish-helper"));
    }

    @Test
    void writesAndReparsesClassifierAndType() {
        ProjectConfig config = writer.defaultApplicationConfig("hello", "com.example", "com.example.Main")
                .withDependencyMetadata(Map.of(
                        DependencyMetadata.key("dependencies", "io.netty:netty-transport-native-epoll"),
                        new DependencyMetadata(
                                "dependencies",
                                "io.netty:netty-transport-native-epoll",
                                "4.1.100.Final",
                                null,
                                false,
                                null,
                                false,
                                false,
                                List.of(),
                                "linux-x86_64",
                                "jar")));
        config = writer.addDependency(
                config, DependencySection.MAIN, "io.netty:netty-transport-native-epoll", "4.1.100.Final");

        String toml = writer.write(config);
        ProjectConfig parsed = parser.parse(toml);

        assertTrue(toml.contains(
                "\"io.netty:netty-transport-native-epoll\" = "
                        + "{ version = \"4.1.100.Final\", classifier = \"linux-x86_64\", type = \"jar\" }"));
        assertEquals(config.dependencyMetadata(), parsed.dependencyMetadata());
        assertEquals("4.1.100.Final", parsed.dependencies().get("io.netty:netty-transport-native-epoll"));
    }

    @Test
    void versionRefMutationPreservesExistingDependencyMetadata() {
        ProjectConfig config = parser.parse("""
                [project]
                name = "hello"
                version = "0.1.0"
                group = "com.example"
                java = "21"

                [versions]
                core = "1.0.0"

                [dependencies]
                "com.example:core" = { version = "1.0.0", optional = true, exclusions = [{ group = "com.example", artifact = "legacy-logging" }] }
                """);

        config = writer.addVersionRefDependency(
                config,
                DependencySection.MAIN,
                "com.example:core",
                "core",
                "1.0.0");
        String toml = writer.write(config);
        ProjectConfig parsed = parser.parse(toml);
        DependencyMetadata metadata = parsed.dependencyMetadata()
                .get(DependencyMetadata.key("dependencies", "com.example:core"));

        assertTrue(toml.contains("\"com.example:core\" = { versionRef = \"core\", optional = true, exclusions = [{ group = \"com.example\", artifact = \"legacy-logging\" }] }"));
        assertEquals("core", metadata.versionRef());
        assertTrue(metadata.optional());
        assertEquals(List.of(new DependencyExclusionSpec("com.example", "legacy-logging")), metadata.exclusions());
    }

    @Test
    void removesNonPublishDependencyMetadataWhenRemovingDependency() {
        ProjectConfig config = writer.defaultApplicationConfig("hello", "com.example", "com.example.Main")
                .withDependencyMetadata(Map.of(
                        DependencyMetadata.key("dependencies", "com.example:core"),
                        new DependencyMetadata(
                                "dependencies",
                                "com.example:core",
                                "1.0.0",
                                false,
                                null,
                                true,
                                false,
                                List.of())));
        config = writer.addDependency(config, DependencySection.MAIN, "com.example:core", "1.0.0");
        config = writer.removeDependency(config, DependencySection.MAIN, "com.example:core");

        ProjectConfig parsed = parser.parse(writer.write(config));

        assertFalse(parsed.dependencies().containsKey("com.example:core"));
        assertFalse(parsed.dependencyMetadata().containsKey(DependencyMetadata.key("dependencies", "com.example:core")));
    }


}
