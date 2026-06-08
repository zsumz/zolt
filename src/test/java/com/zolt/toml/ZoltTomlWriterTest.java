package com.zolt.toml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.project.BuildMetadataSettings;
import com.zolt.project.BuildSettings;
import com.zolt.project.CompilerSettings;
import com.zolt.project.DependencySection;
import com.zolt.project.NativeSettings;
import com.zolt.project.PackageMode;
import com.zolt.project.PackageSettings;
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
        assertEquals(original.apiDependencies(), parsed.apiDependencies());
        assertEquals(original.managedApiDependencies(), parsed.managedApiDependencies());
        assertEquals(original.workspaceApiDependencies(), parsed.workspaceApiDependencies());
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
        assertEquals(original.packageSettings(), parsed.packageSettings());
    }

    @Test
    void writesApiDependencies() {
        ProjectConfig config = parser.parse("""
                [project]
                name = "web"
                version = "0.1.0"
                group = "com.acme"
                java = "21"

                [api.dependencies]
                "com.acme:managed-contract" = {}
                "com.acme:shared-contract" = { workspace = "modules/shared-contract" }
                "com.fasterxml.jackson.core:jackson-annotations" = "2.20.0"
                """);

        String toml = writer.write(config);
        ProjectConfig parsed = parser.parse(toml);

        assertTrue(toml.contains("[api.dependencies]\n"));
        assertTrue(toml.contains("\"com.acme:managed-contract\" = {}"));
        assertTrue(toml.contains("\"com.acme:shared-contract\" = { workspace = \"modules/shared-contract\" }"));
        assertTrue(toml.contains("\"com.fasterxml.jackson.core:jackson-annotations\" = \"2.20.0\""));
        assertEquals(config.apiDependencies(), parsed.apiDependencies());
        assertEquals(config.managedApiDependencies(), parsed.managedApiDependencies());
        assertEquals(config.workspaceApiDependencies(), parsed.workspaceApiDependencies());
    }

    @Test
    void preservesApiDependenciesWhenEditingOtherSections() {
        ProjectConfig config = parser.parse("""
                [project]
                name = "web"
                version = "0.1.0"
                group = "com.acme"
                java = "21"

                [api.dependencies]
                "com.acme:contract" = "1.0.0"
                """);

        config = writer.addDependency(config, DependencySection.TEST, "org.junit.jupiter:junit-jupiter", "5.11.4");
        config = writer.addPlatform(config, "com.acme:enterprise-platform", "2026.1.0");

        ProjectConfig parsed = parser.parse(writer.write(config));

        assertEquals("1.0.0", parsed.apiDependencies().get("com.acme:contract"));
        assertEquals("5.11.4", parsed.testDependencies().get("org.junit.jupiter:junit-jupiter"));
        assertEquals("2026.1.0", parsed.platforms().get("com.acme:enterprise-platform"));
    }

    @Test
    void writesRuntimeAndProvidedDependencies() {
        ProjectConfig config = writer.defaultApplicationConfig("web", "com.acme", "com.acme.Main");
        config = writer.addManagedDependency(config, DependencySection.RUNTIME, "com.h2database:h2");
        config = writer.addDependency(
                config,
                DependencySection.PROVIDED,
                "jakarta.servlet:jakarta.servlet-api",
                "6.1.0");
        config = writer.addPlatform(config, "org.springframework.boot:spring-boot-dependencies", "4.0.6");

        String toml = writer.write(config);
        ProjectConfig parsed = parser.parse(toml);

        assertTrue(toml.contains("[runtime.dependencies]\n\"com.h2database:h2\" = {}"));
        assertTrue(toml.contains("[provided.dependencies]\n\"jakarta.servlet:jakarta.servlet-api\" = \"6.1.0\""));
        assertTrue(parsed.managedRuntimeDependencies().contains("com.h2database:h2"));
        assertEquals("6.1.0", parsed.providedDependencies().get("jakarta.servlet:jakarta.servlet-api"));
        assertEquals("4.0.6", parsed.platforms().get("org.springframework.boot:spring-boot-dependencies"));
    }

    @Test
    void editingRuntimeDependencyRemovesConflictingMainScopeDependency() {
        ProjectConfig config = parser.parse("""
                [project]
                name = "web"
                version = "0.1.0"
                group = "com.acme"
                java = "21"

                [dependencies]
                "com.h2database:h2" = "2.4.240"
                """);

        config = writer.addManagedDependency(config, DependencySection.RUNTIME, "com.h2database:h2");

        ProjectConfig parsed = parser.parse(writer.write(config));

        assertTrue(parsed.dependencies().isEmpty());
        assertTrue(parsed.managedRuntimeDependencies().contains("com.h2database:h2"));
    }

    @Test
    void removingProvidedDependencyPreservesRuntimeDependency() {
        ProjectConfig config = parser.parse("""
                [project]
                name = "web"
                version = "0.1.0"
                group = "com.acme"
                java = "21"

                [runtime.dependencies]
                "com.h2database:h2" = "2.4.240"

                [provided.dependencies]
                "jakarta.servlet:jakarta.servlet-api" = "6.1.0"
                """);

        config = writer.removeDependency(config, DependencySection.PROVIDED, "jakarta.servlet:jakarta.servlet-api");

        ProjectConfig parsed = parser.parse(writer.write(config));

        assertEquals("2.4.240", parsed.runtimeDependencies().get("com.h2database:h2"));
        assertTrue(parsed.providedDependencies().isEmpty());
    }

    @Test
    void writesDevDependencies() {
        ProjectConfig config = writer.defaultApplicationConfig("web", "com.acme", "com.acme.Main");
        config = writer.addManagedDependency(config, DependencySection.DEV, "org.springframework.boot:spring-boot-devtools");
        config = writer.addDependency(config, DependencySection.DEV, "com.acme:local-tool", "1.0.0");

        String toml = writer.write(config);
        ProjectConfig parsed = parser.parse(toml);

        assertTrue(toml.contains("[dev.dependencies]\n\"com.acme:local-tool\" = \"1.0.0\""));
        assertTrue(toml.contains("\"org.springframework.boot:spring-boot-devtools\" = {}"));
        assertEquals("1.0.0", parsed.devDependencies().get("com.acme:local-tool"));
        assertTrue(parsed.managedDevDependencies().contains("org.springframework.boot:spring-boot-devtools"));
    }

    @Test
    void editingDevDependencyRemovesConflictingRuntimeDependency() {
        ProjectConfig config = parser.parse("""
                [project]
                name = "web"
                version = "0.1.0"
                group = "com.acme"
                java = "21"

                [runtime.dependencies]
                "com.acme:local-tool" = "1.0.0"
                """);

        config = writer.addDependency(config, DependencySection.DEV, "com.acme:local-tool", "2.0.0");

        ProjectConfig parsed = parser.parse(writer.write(config));

        assertTrue(parsed.runtimeDependencies().isEmpty());
        assertEquals("2.0.0", parsed.devDependencies().get("com.acme:local-tool"));
    }

    @Test
    void editingMainDependencyRemovesConflictingApiDependency() {
        ProjectConfig config = parser.parse("""
                [project]
                name = "web"
                version = "0.1.0"
                group = "com.acme"
                java = "21"

                [api.dependencies]
                "com.acme:contract" = "1.0.0"
                """);

        config = writer.addDependency(config, DependencySection.MAIN, "com.acme:contract", "2.0.0");

        ProjectConfig parsed = parser.parse(writer.write(config));

        assertTrue(parsed.apiDependencies().isEmpty());
        assertEquals("2.0.0", parsed.dependencies().get("com.acme:contract"));
    }

    @Test
    void editingApiDependencyRemovesConflictingMainDependency() {
        ProjectConfig config = parser.parse("""
                [project]
                name = "web"
                version = "0.1.0"
                group = "com.acme"
                java = "21"

                [dependencies]
                "com.acme:contract" = "1.0.0"
                """);

        config = writer.addDependency(config, DependencySection.API, "com.acme:contract", "2.0.0");

        ProjectConfig parsed = parser.parse(writer.write(config));

        assertTrue(parsed.dependencies().isEmpty());
        assertEquals("2.0.0", parsed.apiDependencies().get("com.acme:contract"));
    }

    @Test
    void removingMainDependencyPreservesApiDependency() {
        ProjectConfig config = parser.parse("""
                [project]
                name = "web"
                version = "0.1.0"
                group = "com.acme"
                java = "21"

                [api.dependencies]
                "com.acme:contract" = "1.0.0"
                """);

        config = writer.removeDependency(config, DependencySection.MAIN, "com.acme:contract");

        ProjectConfig parsed = parser.parse(writer.write(config));

        assertTrue(parsed.dependencies().isEmpty());
        assertEquals("1.0.0", parsed.apiDependencies().get("com.acme:contract"));
    }

    @Test
    void editsApiDependenciesAcrossVersionedManagedAndWorkspaceForms() {
        ProjectConfig config = parser.parse("""
                [project]
                name = "web"
                version = "0.1.0"
                group = "com.acme"
                java = "21"

                [api.dependencies]
                "com.acme:managed-contract" = "1.0.0"
                "com.acme:shared-contract" = { workspace = "modules/shared-contract" }
                """);

        config = writer.addManagedDependency(config, DependencySection.API, "com.acme:managed-contract");
        config = writer.addDependency(config, DependencySection.API, "com.acme:shared-contract", "2.0.0");
        config = writer.removeDependency(config, DependencySection.API, "com.acme:missing-contract");

        ProjectConfig parsed = parser.parse(writer.write(config));

        assertTrue(parsed.managedApiDependencies().contains("com.acme:managed-contract"));
        assertFalse(parsed.apiDependencies().containsKey("com.acme:managed-contract"));
        assertEquals("2.0.0", parsed.apiDependencies().get("com.acme:shared-contract"));
        assertFalse(parsed.workspaceApiDependencies().containsKey("com.acme:shared-contract"));
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
    void writesPackageSettingsWhenConfigured() {
        ProjectConfig original = writer.defaultApplicationConfig("hello", "com.example", "com.example.Main")
                .withPackageSettings(new PackageSettings(PackageMode.SPRING_BOOT));

        String toml = writer.write(original);
        ProjectConfig parsed = parser.parse(toml);

        assertTrue(toml.contains("[package]\n"));
        assertTrue(toml.contains("mode = \"spring-boot\""));
        assertEquals(original.packageSettings(), parsed.packageSettings());
    }

    @Test
    void writesBuildMetadataSettingsWhenConfigured() {
        ProjectConfig original = writer.defaultApplicationConfig("hello", "com.example", "com.example.Main")
                .withBuildSettings(new BuildSettings(
                        "src/main/java",
                        "src/test/java",
                        "target/classes",
                        "target/test-classes",
                        List.of("src/test/java"),
                        new BuildMetadataSettings(true, true, true)));

        String toml = writer.write(original);
        ProjectConfig parsed = parser.parse(toml);

        assertTrue(toml.contains("[build.metadata]\n"));
        assertTrue(toml.contains("buildInfo = true"));
        assertTrue(toml.contains("git = true"));
        assertTrue(toml.contains("reproducible = true"));
        assertEquals(original.build().metadata(), parsed.build().metadata());
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
    void preservesPackageSettingsWhenEditingDependencies() {
        ProjectConfig config = writer.defaultApplicationConfig("hello", "com.example", "com.example.Main")
                .withPackageSettings(new PackageSettings(PackageMode.UBER));
        config = writer.addDependency(config, DependencySection.MAIN, "com.example:app", "1.0.0");
        config = writer.addManagedDependency(config, DependencySection.TEST, "com.example:test-helper");

        ProjectConfig parsed = parser.parse(writer.write(config));

        assertEquals(config.packageSettings(), parsed.packageSettings());
    }

    @Test
    void preservesBuildMetadataSettingsWhenEditingDependencies() {
        ProjectConfig config = writer.defaultApplicationConfig("hello", "com.example", "com.example.Main")
                .withBuildSettings(new BuildSettings(
                        "src/main/java",
                        "src/test/java",
                        "target/classes",
                        "target/test-classes",
                        List.of("src/test/java"),
                        new BuildMetadataSettings(true, false, true)));
        config = writer.addDependency(config, DependencySection.MAIN, "com.example:app", "1.0.0");
        config = writer.addManagedDependency(config, DependencySection.TEST, "com.example:test-helper");

        ProjectConfig parsed = parser.parse(writer.write(config));

        assertEquals(config.build().metadata(), parsed.build().metadata());
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
        config = writer.addManagedDependency(config, DependencySection.PROCESSOR, "com.example:processor");
        config = writer.addManagedDependency(config, DependencySection.TEST_PROCESSOR, "com.example:test-processor");

        ProjectConfig parsed = parser.parse(writer.write(config));

        assertTrue(parsed.managedDependencies().contains("com.example:app"));
        assertTrue(parsed.managedTestDependencies().contains("com.example:test-tool"));
        assertTrue(parsed.managedAnnotationProcessors().contains("com.example:processor"));
        assertTrue(parsed.managedTestAnnotationProcessors().contains("com.example:test-processor"));
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
