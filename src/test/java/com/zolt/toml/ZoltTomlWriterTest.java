package com.zolt.toml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.project.BuildSettings;
import com.zolt.project.CompilerSettings;
import com.zolt.project.DependencySection;
import com.zolt.project.NativeSettings;
import com.zolt.project.ProjectConfig;
import com.zolt.project.ProjectMetadata;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class ZoltTomlWriterTest {
    private final ZoltTomlParser parser = new ZoltTomlParser();
    private final ZoltTomlWriter writer = new ZoltTomlWriter();

    @Test
    void writesDefaultConfig() {
        ProjectConfig config = writer.defaultApplicationConfig("hello", "com.example", "com.example.Main");

        String toml = writer.write(config);

        assertEquals("""
                [project]
                name = "hello"
                version = "0.1.0"
                group = "com.example"
                java = "21"
                main = "com.example.Main"

                [repositories]
                "central" = "https://repo.maven.apache.org/maven2"

                [platforms]

                [dependencies]

                [test.dependencies]

                [build]
                source = "src/main/java"
                test = "src/test/java"
                output = "target/classes"
                testOutput = "target/test-classes"
                """, toml);
    }

    @Test
    void writtenConfigParsesBackIntoModel() {
        ProjectConfig original = writer.defaultApplicationConfig("hello", "com.example", "com.example.Main");
        ProjectConfig parsed = parser.parse(writer.write(original));

        assertEquals(original.project(), parsed.project());
        assertEquals(original.repositories(), parsed.repositories());
        assertEquals(original.platforms(), parsed.platforms());
        assertEquals(original.dependencies(), parsed.dependencies());
        assertEquals(original.managedDependencies(), parsed.managedDependencies());
        assertEquals(original.workspaceDependencies(), parsed.workspaceDependencies());
        assertEquals(original.testDependencies(), parsed.testDependencies());
        assertEquals(original.managedTestDependencies(), parsed.managedTestDependencies());
        assertEquals(original.workspaceTestDependencies(), parsed.workspaceTestDependencies());
        assertEquals(original.annotationProcessors(), parsed.annotationProcessors());
        assertEquals(original.managedAnnotationProcessors(), parsed.managedAnnotationProcessors());
        assertEquals(original.testAnnotationProcessors(), parsed.testAnnotationProcessors());
        assertEquals(original.managedTestAnnotationProcessors(), parsed.managedTestAnnotationProcessors());
        assertEquals(original.build(), parsed.build());
        assertEquals(original.compilerSettings(), parsed.compilerSettings());
    }

    @Test
    void writesCompilerSettingsWhenConfigured() {
        ProjectConfig config = configWithCompilerSettings();

        String toml = writer.write(config);
        ProjectConfig parsed = parser.parse(toml);

        assertTrue(toml.contains("[compiler]\n"));
        assertTrue(toml.contains("generatedSources = \"build/generated/main\""));
        assertTrue(toml.contains("generatedTestSources = \"build/generated/test\""));
        assertEquals(config.compilerSettings(), parsed.compilerSettings());
    }

    @Test
    void writesNativeSettingsWhenConfigured() {
        ProjectConfig original = configWithNativeSettings();

        ProjectConfig parsed = parser.parse(writer.write(original));

        assertEquals(original.nativeSettings(), parsed.nativeSettings());
    }

    @Test
    void preservesCompilerSettingsWhenEditingDependencies() {
        ProjectConfig config = configWithCompilerSettings();
        config = writer.addDependency(config, DependencySection.MAIN, "com.example:app", "1.0.0");
        config = writer.addManagedDependency(config, DependencySection.TEST, "com.example:test-helper");

        ProjectConfig parsed = parser.parse(writer.write(config));

        assertEquals(config.compilerSettings(), parsed.compilerSettings());
    }

    @Test
    void addsDependenciesToCorrectSections() {
        ProjectConfig config = writer.defaultApplicationConfig("hello", "com.example", "com.example.Main");
        config = writer.addDependency(config, DependencySection.MAIN, "com.google.guava:guava", "33.4.0-jre");
        config = writer.addDependency(config, DependencySection.TEST, "org.junit.jupiter:junit-jupiter", "5.11.4");

        ProjectConfig parsed = parser.parse(writer.write(config));

        assertEquals("33.4.0-jre", parsed.dependencies().get("com.google.guava:guava"));
        assertEquals("5.11.4", parsed.testDependencies().get("org.junit.jupiter:junit-jupiter"));
    }

    @Test
    void preservesNativeSettingsWhenEditingDependencies() {
        ProjectConfig config = configWithNativeSettings();
        config = writer.addDependency(config, DependencySection.MAIN, "com.google.guava:guava", "33.4.0-jre");
        config = writer.removeDependency(config, DependencySection.MAIN, "com.google.guava:guava");

        ProjectConfig parsed = parser.parse(writer.write(config));

        assertEquals(config.nativeSettings(), parsed.nativeSettings());
    }

    @Test
    void preservesExplicitJavaTestSourceRootsWhenEditingDependencies() {
        ProjectConfig config = new ProjectConfig(
                new ProjectMetadata("hello", "0.1.0", "com.example", "21", Optional.of("com.example.Main")),
                ProjectConfig.defaultRepositories(),
                Map.of(),
                Map.of(),
                new BuildSettings(
                        "src/main/java",
                        "src/test/java",
                        "target/classes",
                        "target/test-classes",
                        List.of("src/test/java", "src/integration-test/java")));
        config = writer.addDependency(config, DependencySection.TEST, "org.junit.jupiter:junit-jupiter", "5.11.4");

        String toml = writer.write(config);
        ProjectConfig parsed = parser.parse(toml);

        assertTrue(toml.contains("[test.sources]\njava = [\"src/test/java\", \"src/integration-test/java\"]"));
        assertEquals(config.build().testSources(), parsed.build().testSources());
        assertEquals("5.11.4", parsed.testDependencies().get("org.junit.jupiter:junit-jupiter"));
    }

    @Test
    void removesDependenciesFromCorrectSections() {
        ProjectConfig config = writer.defaultApplicationConfig("hello", "com.example", "com.example.Main");
        config = writer.addDependency(config, DependencySection.MAIN, "com.google.guava:guava", "33.4.0-jre");
        config = writer.addDependency(config, DependencySection.TEST, "org.junit.jupiter:junit-jupiter", "5.11.4");
        config = writer.removeDependency(config, DependencySection.MAIN, "com.google.guava:guava");

        ProjectConfig parsed = parser.parse(writer.write(config));

        assertTrue(parsed.dependencies().isEmpty());
        assertEquals("5.11.4", parsed.testDependencies().get("org.junit.jupiter:junit-jupiter"));
    }

    @Test
    void formatsDependenciesDeterministically() {
        ProjectConfig config = writer.defaultApplicationConfig("hello", "com.example", "com.example.Main");
        config = writer.addDependency(config, DependencySection.MAIN, "org.slf4j:slf4j-api", "2.0.16");
        config = writer.addDependency(config, DependencySection.MAIN, "com.google.guava:guava", "33.4.0-jre");

        String toml = writer.write(config);

        assertTrue(toml.indexOf("\"com.google.guava:guava\"") < toml.indexOf("\"org.slf4j:slf4j-api\""));
    }

    @Test
    void writesManagedDependencies() {
        ProjectConfig config = new ProjectConfig(
                new ProjectMetadata("spring", "0.1.0", "com.example", "21", Optional.empty()),
                ProjectConfig.defaultRepositories(),
                Map.of("org.springframework.boot:spring-boot-dependencies", "4.0.6"),
                Map.of(),
                Set.of("org.springframework.boot:spring-boot-starter-webmvc"),
                Map.of(),
                Set.of("org.junit.jupiter:junit-jupiter"),
                BuildSettings.defaults(),
                NativeSettings.defaults());

        String toml = writer.write(config);
        ProjectConfig parsed = parser.parse(toml);

        assertTrue(toml.contains("\"org.springframework.boot:spring-boot-starter-webmvc\" = {}"));
        assertEquals(config.platforms(), parsed.platforms());
        assertEquals(config.managedDependencies(), parsed.managedDependencies());
        assertEquals(config.managedTestDependencies(), parsed.managedTestDependencies());
    }

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
        ProjectConfig config = new ProjectConfig(
                new ProjectMetadata("micronaut", "0.1.0", "com.example", "21", Optional.empty()),
                ProjectConfig.defaultRepositories(),
                Map.of("io.micronaut.platform:micronaut-platform", "5.0.0"),
                Map.of(),
                Set.of(),
                Map.of(),
                Set.of(),
                Map.of("org.mapstruct:mapstruct-processor", "1.6.3"),
                Set.of("io.micronaut:micronaut-inject-java"),
                Map.of("com.example:test-processor", "1.0.0"),
                Set.of("io.micronaut:micronaut-inject-java"),
                BuildSettings.defaults(),
                NativeSettings.defaults());

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
        ProjectConfig config = new ProjectConfig(
                new ProjectMetadata("micronaut", "0.1.0", "com.example", "21", Optional.empty()),
                ProjectConfig.defaultRepositories(),
                Map.of(),
                Map.of(),
                Set.of(),
                Map.of(),
                Set.of(),
                Map.of("org.mapstruct:mapstruct-processor", "1.6.3"),
                Set.of("io.micronaut:micronaut-inject-java"),
                Map.of("com.example:test-processor", "1.0.0"),
                Set.of(),
                BuildSettings.defaults(),
                NativeSettings.defaults());

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
    void addsManagedDependenciesToCorrectSections() {
        ProjectConfig config = writer.defaultApplicationConfig("hello", "com.example", "com.example.Main");
        config = writer.addManagedDependency(config, DependencySection.MAIN, "com.example:app");
        config = writer.addManagedDependency(config, DependencySection.TEST, "com.example:test-tool");

        ProjectConfig parsed = parser.parse(writer.write(config));

        assertTrue(parsed.managedDependencies().contains("com.example:app"));
        assertTrue(parsed.managedTestDependencies().contains("com.example:test-tool"));
    }

    @Test
    void explicitDependencyReplacesManagedDependency() {
        ProjectConfig config = writer.defaultApplicationConfig("hello", "com.example", "com.example.Main");
        config = writer.addManagedDependency(config, DependencySection.MAIN, "com.example:app");
        config = writer.addDependency(config, DependencySection.MAIN, "com.example:app", "1.0.0");

        ProjectConfig parsed = parser.parse(writer.write(config));

        assertEquals("1.0.0", parsed.dependencies().get("com.example:app"));
        assertTrue(parsed.managedDependencies().isEmpty());
    }

    @Test
    void addsAndRemovesPlatforms() {
        ProjectConfig config = writer.defaultApplicationConfig("hello", "com.example", "com.example.Main");
        config = writer.addPlatform(config, "com.example:enterprise-platform", "2026.1.0");
        config = writer.removePlatform(config, "com.example:enterprise-platform");

        ProjectConfig parsed = parser.parse(writer.write(config));

        assertTrue(parsed.platforms().isEmpty());
    }

    private static ProjectConfig configWithNativeSettings() {
        return new ProjectConfig(
                new ProjectMetadata("hello", "0.1.0", "com.example", "21", Optional.of("com.example.Main")),
                ProjectConfig.defaultRepositories(),
                Map.of(),
                Map.of(),
                BuildSettings.defaults(),
                new NativeSettings("hello-native", "target/native-custom", List.of("--no-fallback")));
    }

    private static ProjectConfig configWithCompilerSettings() {
        return new ProjectConfig(
                new ProjectMetadata("hello", "0.1.0", "com.example", "21", Optional.of("com.example.Main")),
                ProjectConfig.defaultRepositories(),
                Map.of(),
                Map.of(),
                Set.of(),
                Map.of(),
                Set.of(),
                Map.of(),
                Set.of(),
                Map.of(),
                Set.of(),
                BuildSettings.defaults(),
                NativeSettings.defaults(),
                new CompilerSettings("build/generated/main", "build/generated/test"));
    }
}
