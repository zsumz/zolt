package com.zolt.explain.emit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

        DraftZoltToml draft = mapper.fromMaven(new MavenStaticProjectInspector().inspect(tempDir));
        ProjectConfig config = draft.config();
        String rendered = render(draft);

        assertEquals("8", config.project().java());
        assertTrue(hasLine(rendered, "java = \"8\""), () -> rendered);
        assertFalse(hasLine(rendered, "# java = \"8\""), () -> rendered);
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

    @Test
    void mavenDraftCommentsUnknownJavaFromExecutionScopedCompilerConfig() throws IOException {
        Files.writeString(tempDir.resolve("pom.xml"), """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example</groupId>
                  <artifactId>execution-java</artifactId>
                  <version>1.0.0</version>
                  <build>
                    <plugins>
                      <plugin>
                        <groupId>org.apache.maven.plugins</groupId>
                        <artifactId>maven-compiler-plugin</artifactId>
                        <version>3.13.0</version>
                        <executions>
                          <execution>
                            <goals>
                              <goal>compile</goal>
                            </goals>
                            <configuration>
                              <source>8</source>
                              <target>8</target>
                            </configuration>
                          </execution>
                        </executions>
                      </plugin>
                    </plugins>
                  </build>
                </project>
                """);

        DraftZoltToml draft = mapper.fromMaven(new MavenStaticProjectInspector().inspect(tempDir));
        String rendered = render(draft);

        assertTrue(rendered.contains("# Review items:"), () -> rendered);
        assertTrue(rendered.contains("Project Java version could not be determined"), () -> rendered);
        assertTrue(hasLine(rendered, "# java = \"unknown\""), () -> rendered);
        assertFalse(hasLine(rendered, "java = \"unknown\""), () -> rendered);
    }

    @Test
    void gradleDraftCommentsUnknownJavaWithoutDetectableToolchain() throws IOException {
        Files.writeString(tempDir.resolve("settings.gradle"), "rootProject.name = 'unknown-gradle'\n");
        Files.writeString(tempDir.resolve("build.gradle"), "plugins { id 'java' }\n");

        DraftZoltToml draft = mapper.fromGradle(new GradleStaticProjectInspector().inspect(tempDir));
        String rendered = render(draft);

        assertTrue(rendered.contains("# Review items:"), () -> rendered);
        assertTrue(rendered.contains("Project Java version could not be determined"), () -> rendered);
        assertTrue(hasLine(rendered, "# java = \"unknown\""), () -> rendered);
        assertFalse(hasLine(rendered, "java = \"unknown\""), () -> rendered);
    }

    private static String render(DraftZoltToml draft) {
        return new DraftZoltTomlRenderer().render(draft, JavaVersionNotationEmitTest::projectOnlyToml);
    }

    private static String projectOnlyToml(ProjectConfig config) {
        return """
                [project]
                name = "%s"
                version = "%s"
                group = "%s"
                java = "%s"

                """.formatted(
                config.project().name(),
                config.project().version(),
                config.project().group(),
                config.project().java());
    }

    private static boolean hasLine(String rendered, String expected) {
        return rendered.lines().anyMatch(expected::equals);
    }
}
