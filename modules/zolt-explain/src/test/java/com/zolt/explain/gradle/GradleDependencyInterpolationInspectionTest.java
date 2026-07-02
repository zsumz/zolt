package com.zolt.explain.gradle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class GradleDependencyInterpolationInspectionTest {
    @TempDir
    private Path tempDir;

    private final GradleStaticProjectInspector inspector = new GradleStaticProjectInspector();

    @Test
    void resolvesDependencyVersionsFromExtAndGradleProperties() throws IOException {
        Files.writeString(tempDir.resolve("settings.gradle"), "rootProject.name = 'interpolated'\n");
        Files.writeString(tempDir.resolve("gradle.properties"), "gsonVersion=2.11.0\n");
        Files.writeString(tempDir.resolve("build.gradle"), """
                plugins { id 'java' }
                ext {
                    slf4jVersion = '2.0.13'
                    junitVersion = '5.10.2'
                }
                dependencies {
                    implementation "org.slf4j:slf4j-api:$slf4jVersion"
                    implementation "com.google.code.gson:gson:${gsonVersion}"
                    testImplementation "org.junit.jupiter:junit-jupiter:$junitVersion"
                }
                """);

        GradleInspectionResult result = inspector.inspect(tempDir);
        GradleProjectInspection project = result.projects().getFirst();

        assertTrue(project.dependencies().stream()
                .anyMatch(dependency -> dependency.resolvedCoordinate().equals("org.slf4j:slf4j-api:2.0.13")));
        assertTrue(project.dependencies().stream()
                .anyMatch(dependency -> dependency.resolvedCoordinate().equals("com.google.code.gson:gson:2.11.0")));
        assertTrue(project.dependencies().stream()
                .anyMatch(dependency -> dependency.resolvedCoordinate().equals("org.junit.jupiter:junit-jupiter:5.10.2")));
        assertFalse(project.dependencies().stream().anyMatch(dependency -> dependency.resolvedCoordinate().contains("$")));
    }

    @Test
    void unresolvedDependencyVersionPlaceholdersBecomeSignalsNotDependencies() throws IOException {
        Files.writeString(tempDir.resolve("settings.gradle"), "rootProject.name = 'unresolved'\n");
        Files.writeString(tempDir.resolve("build.gradle"), """
                plugins { id 'java' }
                dependencies {
                    implementation "org.slf4j:slf4j-api:$slf4jVersion"
                    implementation "com.google.code.gson:gson:${gsonVersion}"
                }
                """);

        GradleInspectionResult result = inspector.inspect(tempDir);
        GradleProjectInspection project = result.projects().getFirst();

        assertEquals(0, project.dependencies().size(), () -> "unresolved placeholders must not be emitted");
        assertEquals(
                2,
                result.signals().stream()
                        .filter(signal -> signal.id().equals("gradle.dependency.dynamic-version"))
                        .filter(signal -> signal.message().contains("version-policy rule: no-interpolation"))
                        .count());
    }
}
