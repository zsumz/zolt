package sh.zolt.toml.dependency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import sh.zolt.project.DependencySection;
import sh.zolt.project.ProjectConfig;
import sh.zolt.toml.ZoltTomlParser;
import sh.zolt.toml.ZoltTomlWriter;
import org.junit.jupiter.api.Test;

final class ZoltTomlWorkspaceProcessorsWriterTest {
    private final ZoltTomlParser parser = new ZoltTomlParser();
    private final ZoltTomlWriter writer = new ZoltTomlWriter();

    @Test
    void writesWorkspaceDependencies() {
        ProjectConfig config = parser.parse("""
                [project]
                name = "api"
                version = "0.1.0"
                group = "com.acme"
                java = "21"

                [dependencies]
                "com.acme:core" = { workspace = "modules/core" }

                [test.dependencies]
                "com.acme:test-fixtures" = { workspace = "modules/test-fixtures" }
                """);

        String toml = writer.write(config);
        ProjectConfig parsed = parser.parse(toml);

        assertTrue(toml.contains("\"com.acme:core\" = { workspace = \"modules/core\" }"));
        assertTrue(toml.contains("\"com.acme:test-fixtures\" = { workspace = \"modules/test-fixtures\" }"));
        assertEquals(config.workspaceDependencies(), parsed.workspaceDependencies());
        assertEquals(config.workspaceTestDependencies(), parsed.workspaceTestDependencies());
    }

    @Test
    void editingDependenciesRemovesConflictingWorkspaceDependency() {
        ProjectConfig config = parser.parse("""
                [project]
                name = "api"
                version = "0.1.0"
                group = "com.acme"
                java = "21"

                [dependencies]
                "com.acme:core" = { workspace = "modules/core" }
                """);

        config = writer.addDependency(config, DependencySection.MAIN, "com.acme:core", "1.0.0");
        ProjectConfig parsed = parser.parse(writer.write(config));

        assertEquals("1.0.0", parsed.dependencies().get("com.acme:core"));
        assertTrue(parsed.workspaceDependencies().isEmpty());
    }


    @Test
    void writesAnnotationProcessorDeclarationsDeterministically() {
        ProjectConfig config = parser.parse("""
                [project]
                name = "micronaut"
                version = "0.1.0"
                group = "com.example"
                java = "21"

                [platforms]
                "io.micronaut.platform:micronaut-platform" = "5.0.0"

                [annotationProcessors]
                "org.mapstruct:mapstruct-processor" = "1.6.3"
                "io.micronaut:micronaut-inject-java" = {}

                [test.annotationProcessors]
                "com.example:test-processor" = "1.0.0"
                "io.micronaut:micronaut-inject-java" = {}
                """);

        String toml = writer.write(config);
        ProjectConfig parsed = parser.parse(toml);

        assertTrue(toml.contains("[annotationProcessors]\n"));
        assertTrue(toml.contains("\"io.micronaut:micronaut-inject-java\" = {}"));
        assertTrue(toml.contains("\"org.mapstruct:mapstruct-processor\" = \"1.6.3\""));
        assertTrue(toml.indexOf("\"io.micronaut:micronaut-inject-java\" = {}")
                < toml.indexOf("\"org.mapstruct:mapstruct-processor\" = \"1.6.3\""));
        assertTrue(toml.contains("[test.annotationProcessors]\n"));
        assertEquals(config.annotationProcessors(), parsed.annotationProcessors());
        assertEquals(config.managedAnnotationProcessors(), parsed.managedAnnotationProcessors());
        assertEquals(config.testAnnotationProcessors(), parsed.testAnnotationProcessors());
        assertEquals(config.managedTestAnnotationProcessors(), parsed.managedTestAnnotationProcessors());
    }

    @Test
    void preservesAnnotationProcessorsWhenEditingDependencies() {
        ProjectConfig config = parser.parse("""
                [project]
                name = "micronaut"
                version = "0.1.0"
                group = "com.example"
                java = "21"

                [annotationProcessors]
                "org.mapstruct:mapstruct-processor" = "1.6.3"
                "io.micronaut:micronaut-inject-java" = {}

                [test.annotationProcessors]
                "com.example:test-processor" = "1.0.0"
                """);

        config = writer.addDependency(config, DependencySection.MAIN, "com.example:app", "1.0.0");
        config = writer.addManagedDependency(config, DependencySection.TEST, "com.example:test-helper");
        config = writer.addPlatform(config, "io.micronaut.platform:micronaut-platform", "5.0.0");

        ProjectConfig parsed = parser.parse(writer.write(config));

        assertEquals(config.annotationProcessors(), parsed.annotationProcessors());
        assertEquals(config.managedAnnotationProcessors(), parsed.managedAnnotationProcessors());
        assertEquals(config.testAnnotationProcessors(), parsed.testAnnotationProcessors());
        assertEquals(config.managedTestAnnotationProcessors(), parsed.managedTestAnnotationProcessors());
    }


    @Test
    void writesWorkspaceAnnotationProcessorsRoundTrip() {
        ProjectConfig config = parser.parse("""
                [project]
                name = "consumer"
                version = "0.1.0"
                group = "com.example"
                java = "21"

                [annotationProcessors]
                "com.example:shape-processor" = { workspace = "modules/shape-processor" }

                [test.annotationProcessors]
                "com.example:test-processor" = { workspace = "modules/test-processor" }
                """);

        String toml = writer.write(config);
        ProjectConfig parsed = parser.parse(toml);

        assertTrue(toml.contains("\"com.example:shape-processor\" = { workspace = \"modules/shape-processor\" }"));
        assertTrue(toml.contains("\"com.example:test-processor\" = { workspace = \"modules/test-processor\" }"));
        assertEquals(config.workspaceAnnotationProcessors(), parsed.workspaceAnnotationProcessors());
        assertEquals(config.workspaceTestAnnotationProcessors(), parsed.workspaceTestAnnotationProcessors());
    }

    @Test
    void editsAnnotationProcessorSectionsWithoutTouchingRuntimeDependencies() {
        ProjectConfig config = parser.parse("""
                [project]
                name = "processor-demo"
                version = "0.1.0"
                group = "com.example"
                java = "21"

                [dependencies]
                "com.example:processor-api" = "1.0.0"

                [annotationProcessors]
                "com.example:processor" = {}

                [test.annotationProcessors]
                "com.example:test-processor" = "1.0.0"
                """);

        config = writer.addDependency(config, DependencySection.PROCESSOR, "com.example:processor", "2.0.0");
        config = writer.addManagedDependency(config, DependencySection.TEST_PROCESSOR, "com.example:test-processor");
        config = writer.removeDependency(config, DependencySection.PROCESSOR, "com.example:missing-processor");

        ProjectConfig parsed = parser.parse(writer.write(config));

        assertEquals("1.0.0", parsed.dependencies().get("com.example:processor-api"));
        assertEquals("2.0.0", parsed.annotationProcessors().get("com.example:processor"));
        assertTrue(parsed.managedAnnotationProcessors().isEmpty());
        assertTrue(parsed.testAnnotationProcessors().isEmpty());
        assertTrue(parsed.managedTestAnnotationProcessors().contains("com.example:test-processor"));
    }

}
