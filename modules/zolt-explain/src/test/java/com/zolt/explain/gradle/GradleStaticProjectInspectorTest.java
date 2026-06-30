package com.zolt.explain.gradle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.explain.ExplainSignal;
import com.zolt.explain.MigrationExplainException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class GradleStaticProjectInspectorTest {
    @TempDir
    private Path tempDir;

    private final GradleStaticProjectInspector inspector = new GradleStaticProjectInspector();

    @Test
    void inspectsSimpleSingleProjectJavaBuild() throws IOException {
        Files.writeString(tempDir.resolve("settings.gradle"), "rootProject.name = 'demo'\n");
        Files.writeString(tempDir.resolve("build.gradle"), """
                plugins {
                    id 'java'
                    id 'application'
                }

                repositories {
                    mavenCentral()
                }

                java {
                    toolchain {
                        languageVersion = JavaLanguageVersion.of(21)
                    }
                }

                dependencies {
                    implementation 'com.google.guava:guava:33.4.8-jre'
                    compileOnly group: 'jakarta.annotation', name: 'jakarta.annotation-api', version: '3.0.0'
                    testImplementation 'org.junit.jupiter:junit-jupiter:5.11.4'
                }
                """);

        GradleInspectionResult result = inspector.inspect(tempDir);

        assertEquals(tempDir.toAbsolutePath().normalize(), result.root());
        assertEquals("settings.gradle", result.settingsFile());
        assertEquals(1, result.projects().size());
        GradleProjectInspection project = result.projects().getFirst();
        assertEquals(Path.of("."), project.path());
        assertEquals("build.gradle", project.buildFile());
        assertEquals("groovy", project.dsl());
        assertEquals("21", project.javaVersion());
        assertTrue(project.plugins().stream().anyMatch(plugin -> plugin.id().equals("java")));
        assertTrue(project.repositories().stream().anyMatch(repository -> repository.kind().equals("mavenCentral")));
        assertTrue(project.dependencies().stream()
                .anyMatch(dependency -> dependency.resolvedCoordinate().equals("com.google.guava:guava:33.4.8-jre")));
        assertTrue(project.dependencies().stream()
                .anyMatch(dependency -> dependency.resolvedCoordinate().equals("jakarta.annotation:jakarta.annotation-api:3.0.0")));
        assertEquals("src/main/java", project.sourceRoots().getFirst());
        assertEquals("src/test/java", project.testSourceRoots().getFirst());
        assertTrue(result.signals().isEmpty());
    }

    @Test
    void reportsMultiProjectIncludesAndMissingBuildFiles() throws IOException {
        Files.writeString(tempDir.resolve("settings.gradle.kts"), """
                rootProject.name = "multi"
                include("app", ":modules:core", "missing")
                """);
        Files.writeString(tempDir.resolve("build.gradle.kts"), "plugins { java }\n");
        Path app = tempDir.resolve("app");
        Path core = tempDir.resolve("modules/core");
        Files.createDirectories(app);
        Files.createDirectories(core);
        Files.writeString(app.resolve("build.gradle.kts"), "plugins { id(\"java-library\") }\n");
        Files.writeString(core.resolve("build.gradle"), "plugins { id 'java' }\n");

        GradleInspectionResult result = inspector.inspect(tempDir);

        assertEquals(3, result.includedProjects().size());
        assertTrue(result.includedProjects().contains("app"));
        assertTrue(result.includedProjects().contains("modules/core"));
        assertEquals(3, result.projects().size());
        assertTrue(result.projects().stream().anyMatch(project -> project.path().equals(Path.of("app"))));
        assertTrue(result.projects().stream().anyMatch(project -> project.path().equals(Path.of("modules/core"))));
        assertTrue(result.signals().stream()
                .anyMatch(signal -> signal.id().equals("gradle.project.missing-build-file")
                        && signal.project().equals("missing")));
    }

    @Test
    void resolvesSimpleVersionCatalogAliases() throws IOException {
        Files.createDirectories(tempDir.resolve("gradle"));
        Files.writeString(tempDir.resolve("gradle/libs.versions.toml"), """
                [versions]
                guava = "33.4.8-jre"

                [libraries]
                guava = { module = "com.google.guava:guava", version.ref = "guava" }
                junit-jupiter = { group = "org.junit.jupiter", name = "junit-jupiter", version = "5.11.4" }
                """);
        Files.writeString(tempDir.resolve("build.gradle"), """
                plugins { id 'java' }
                dependencies {
                    implementation libs.guava
                    testImplementation(libs.junit.jupiter)
                }
                """);

        GradleInspectionResult result = inspector.inspect(tempDir);
        GradleProjectInspection project = result.projects().getFirst();

        assertEquals(2, result.versionCatalogAliases().size());
        assertTrue(project.dependencies().stream()
                .anyMatch(dependency -> dependency.versionCatalogAlias().equals("guava")
                        && dependency.resolvedCoordinate().equals("com.google.guava:guava:33.4.8-jre")));
        assertTrue(project.dependencies().stream()
                .anyMatch(dependency -> dependency.versionCatalogAlias().equals("junit.jupiter")
                        && dependency.resolvedCoordinate().equals("org.junit.jupiter:junit-jupiter:5.11.4")));
    }

    @Test
    void reportsDynamicBuildLogicSignalsWithoutExecutingGradle() throws IOException {
        Files.createDirectories(tempDir.resolve("buildSrc"));
        Files.writeString(tempDir.resolve("settings.gradle"), """
                includeBuild 'build-logic'
                """);
        Files.writeString(tempDir.resolve("build.gradle"), """
                plugins {
                    id 'com.example.java-conventions'
                }

                allprojects {
                    configurations.all {
                        resolutionStrategy.cacheChangingModulesFor 0, 'seconds'
                    }
                }

                dependencies {
                    implementation 'com.example:changing:latest.release'
                }
                dependencies.add('implementation', 'com.example:dynamic:1.0')
                tasks.register('generateStuff')
                """);
        Path marker = tempDir.resolve("executed.txt");
        Path gradlew = tempDir.resolve("gradlew");
        Files.writeString(gradlew, "#!/usr/bin/env sh\nprintf gradle > '" + marker + "'\n");
        assertTrue(gradlew.toFile().setExecutable(true));

        GradleInspectionResult result = inspector.inspect(tempDir);

        assertTrue(result.includedProjects().isEmpty());
        assertFalse(Files.exists(marker));
        assertTrue(result.signals().stream().anyMatch(signal -> signal.id().equals("gradle.build-src.detected")
                && signal.category() == ExplainSignal.Category.MIGRATION_BLOCKER));
        assertTrue(result.signals().stream().anyMatch(signal -> signal.id().equals("gradle.included-build.detected")
                && signal.category() == ExplainSignal.Category.MIGRATION_BLOCKER));
        assertTrue(result.signals().stream().anyMatch(signal -> signal.id().equals("gradle.plugin.convention")
                && signal.category() == ExplainSignal.Category.MIGRATION_BLOCKER));
        assertTrue(result.signals().stream().anyMatch(signal -> signal.id().equals("gradle.imperative-dependency-logic")
                && signal.category() == ExplainSignal.Category.MIGRATION_BLOCKER));
        assertTrue(result.signals().stream().anyMatch(signal -> signal.id().equals("gradle.dependency.dynamic-version")
                && signal.category() == ExplainSignal.Category.NON_DETERMINISM));
        assertTrue(result.signals().stream().anyMatch(signal -> signal.id().equals("gradle.cross-project-build-logic")
                && signal.category() == ExplainSignal.Category.MIGRATION_BLOCKER));
        assertTrue(result.signals().stream().anyMatch(signal -> signal.id().equals("gradle.custom-task.detected")
                && signal.category() == ExplainSignal.Category.BUILDABILITY));
    }

    @Test
    void missingGradleMetadataFailsWithActionableMessage() {
        MigrationExplainException exception = assertThrows(
                MigrationExplainException.class,
                () -> inspector.inspect(tempDir));

        assertTrue(exception.getMessage().contains("Expected settings.gradle"));
        assertTrue(exception.getMessage().contains("pass --cwd"));
    }

}
