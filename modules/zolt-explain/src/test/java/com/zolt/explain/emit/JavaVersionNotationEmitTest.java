package com.zolt.explain.emit;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.zolt.explain.gradle.GradleStaticProjectInspector;
import com.zolt.explain.maven.MavenStaticProjectInspector;
import com.zolt.project.ProjectConfig;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class JavaVersionNotationEmitTest {
    @TempDir
    private Path tempDir;

    private final InspectionToProjectConfig mapper = new InspectionToProjectConfig();

    @Test
    void mavenDraftNormalizesLegacyJavaFeatureNotation() throws IOException {
        Files.writeString(tempDir.resolve("pom.xml"), """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example</groupId>
                  <artifactId>legacy-java</artifactId>
                  <version>1.0.0</version>
                  <properties>
                    <maven.compiler.source>1.8</maven.compiler.source>
                    <maven.compiler.target>1.8</maven.compiler.target>
                  </properties>
                </project>
                """);

        ProjectConfig config = mapper.fromMaven(new MavenStaticProjectInspector().inspect(tempDir)).config();

        assertEquals("8", config.project().java());
    }

    @Test
    void gradleDraftNormalizesUnquotedLegacyJavaFeatureNotation() throws IOException {
        Files.writeString(tempDir.resolve("settings.gradle"), "rootProject.name = 'legacy-gradle'\n");
        Files.writeString(tempDir.resolve("build.gradle"), """
                plugins { id 'java' }
                sourceCompatibility = 1.8
                targetCompatibility = 1.8
                """);

        ProjectConfig config = mapper.fromGradle(new GradleStaticProjectInspector().inspect(tempDir)).config();

        assertEquals("8", config.project().java());
    }

    @Test
    void gradleDraftNormalizesJavaVersionEnumLegacyNotation() throws IOException {
        Files.writeString(tempDir.resolve("settings.gradle"), "rootProject.name = 'legacy-gradle'\n");
        Files.writeString(tempDir.resolve("build.gradle"), """
                plugins { id 'java' }
                sourceCompatibility = JavaVersion.VERSION_1_8
                """);

        ProjectConfig config = mapper.fromGradle(new GradleStaticProjectInspector().inspect(tempDir)).config();

        assertEquals("8", config.project().java());
    }
}
