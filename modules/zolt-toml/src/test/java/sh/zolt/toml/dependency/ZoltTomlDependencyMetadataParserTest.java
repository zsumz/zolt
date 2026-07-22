package sh.zolt.toml.dependency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import sh.zolt.project.DependencyMetadata;
import sh.zolt.project.ProjectConfig;
import sh.zolt.toml.ZoltConfigException;
import sh.zolt.toml.ZoltTomlParser;
import org.junit.jupiter.api.Test;

final class ZoltTomlDependencyMetadataParserTest {
    private final ZoltTomlParser parser = new ZoltTomlParser();

    @Test
    void parsesDependencyMetadata() {
        ProjectConfig config = parser.parse("""
                [project]
                name = "library"
                version = "0.1.0"
                group = "com.example"
                java = "21"

                [dependencies]
                "com.example:core" = { version = "1.0.0", optional = true, exclusions = [{ group = "com.example", artifact = "legacy-logging" }] }
                "com.example:publish-helper" = { version = "2.0.0", publishOnly = true }
                "com.example:managed-publish" = { publishOnly = true }
                """);

        DependencyMetadata core = config.dependencyMetadata()
                .get(DependencyMetadata.key("dependencies", "com.example:core"));
        DependencyMetadata publish = config.dependencyMetadata()
                .get(DependencyMetadata.key("dependencies", "com.example:publish-helper"));
        DependencyMetadata managedPublish = config.dependencyMetadata()
                .get(DependencyMetadata.key("dependencies", "com.example:managed-publish"));
        assertEquals("1.0.0", config.dependencies().get("com.example:core"));
        assertFalse(config.dependencies().containsKey("com.example:publish-helper"));
        assertFalse(config.managedDependencies().contains("com.example:managed-publish"));
        assertTrue(core.optional());
        assertFalse(core.publishOnly());
        assertEquals("com.example:legacy-logging", core.exclusions().getFirst().coordinate());
        assertEquals("2.0.0", publish.version());
        assertTrue(publish.publishOnly());
        assertTrue(managedPublish.managed());
        assertTrue(managedPublish.publishOnly());
    }

    @Test
    void parsesClassifierAndTypeOnVersionedDependency() {
        ProjectConfig config = parser.parse("""
                [project]
                name = "library"
                version = "0.1.0"
                group = "com.example"
                java = "21"

                [dependencies]
                "io.netty:netty-transport-native-epoll" = { version = "4.1.100.Final", classifier = "linux-x86_64" }
                "com.example:native" = { version = "1.0.0", type = "so" }
                """);

        DependencyMetadata netty = config.dependencyMetadata()
                .get(DependencyMetadata.key("dependencies", "io.netty:netty-transport-native-epoll"));
        DependencyMetadata nativeArtifact = config.dependencyMetadata()
                .get(DependencyMetadata.key("dependencies", "com.example:native"));

        assertEquals("4.1.100.Final", config.dependencies().get("io.netty:netty-transport-native-epoll"));
        assertEquals("linux-x86_64", netty.classifier());
        assertEquals(null, netty.type());
        assertEquals("so", nativeArtifact.type());
        assertEquals(null, nativeArtifact.classifier());
    }

    @Test
    void parsesClassifierOnPlatformManagedDependency() {
        ProjectConfig config = parser.parse("""
                [project]
                name = "library"
                version = "0.1.0"
                group = "com.example"
                java = "21"

                [test.dependencies]
                "org.apache.kafka:kafka-clients" = { classifier = "test" }
                """);

        DependencyMetadata metadata = config.dependencyMetadata()
                .get(DependencyMetadata.key("test.dependencies", "org.apache.kafka:kafka-clients"));

        assertTrue(config.managedTestDependencies().contains("org.apache.kafka:kafka-clients"));
        assertTrue(metadata.managed());
        assertEquals("test", metadata.classifier());
    }

    @Test
    void rejectsClassifierOnWorkspaceDependency() {
        ZoltConfigException exception = assertThrows(ZoltConfigException.class, () -> parser.parse("""
                [project]
                name = "library"
                version = "0.1.0"
                group = "com.example"
                java = "21"

                [dependencies]
                "com.example:member" = { workspace = "modules/member", classifier = "tests" }
                """));

        assertTrue(exception.getMessage().contains("Classifier and type apply to external dependency artifacts"));
    }

}
