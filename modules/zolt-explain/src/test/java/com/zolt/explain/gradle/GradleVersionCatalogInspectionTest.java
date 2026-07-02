package com.zolt.explain.gradle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class GradleVersionCatalogInspectionTest {
    @TempDir
    private Path tempDir;

    private final GradleStaticProjectInspector inspector = new GradleStaticProjectInspector();

    @Test
    void resolvesRichVersionCatalogEntriesAndSignalsRanges() throws IOException {
        Files.createDirectories(tempDir.resolve("gradle"));
        Files.writeString(tempDir.resolve("gradle/libs.versions.toml"), """
                [versions]
                junit4 = { require = "[4.12,)", prefer = "4.13.2" }
                commons = { strictly = "[3.12,4[", prefer = "3.14.0" }

                [libraries]
                guava = { module = "com.google.guava:guava", version = { strictly = "[33.0, 34[", prefer = "33.4.8-jre" } }
                junit4 = { module = "junit:junit", version.ref = "junit4" }
                commons-lang3 = { module = "org.apache.commons:commons-lang3", version.ref = "commons" }
                logback = { module = "ch.qos.logback:logback-classic", version = { strictly = "1.5.6" } }
                """);
        Files.writeString(tempDir.resolve("build.gradle"), """
                plugins { id 'java' }
                dependencies {
                    implementation libs.guava
                    implementation libs.commons.lang3
                    implementation libs.logback
                    testImplementation libs.junit4
                }
                """);

        GradleInspectionResult result = inspector.inspect(tempDir);
        GradleProjectInspection project = result.projects().getFirst();

        assertTrue(project.dependencies().stream()
                .anyMatch(dependency -> dependency.versionCatalogAlias().equals("guava")
                        && dependency.resolvedCoordinate().equals("com.google.guava:guava:33.4.8-jre")));
        assertTrue(project.dependencies().stream()
                .anyMatch(dependency -> dependency.versionCatalogAlias().equals("junit4")
                        && dependency.resolvedCoordinate().equals("junit:junit:4.13.2")));
        assertTrue(project.dependencies().stream()
                .anyMatch(dependency -> dependency.versionCatalogAlias().equals("commons.lang3")
                        && dependency.resolvedCoordinate().equals("org.apache.commons:commons-lang3:3.14.0")));
        assertTrue(project.dependencies().stream()
                .anyMatch(dependency -> dependency.versionCatalogAlias().equals("logback")
                        && dependency.resolvedCoordinate().equals("ch.qos.logback:logback-classic:1.5.6")));
        assertEquals(
                3,
                result.signals().stream()
                        .filter(signal -> signal.id().equals("gradle.dependency.dynamic-version"))
                        .filter(signal -> signal.message().contains("version-policy rule: version-range"))
                        .count());
        assertFalse(result.signals().stream()
                .anyMatch(signal -> signal.message().contains("logback")));
    }
}
