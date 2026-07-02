package com.zolt.explain.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

// : plugin coordinates and <name> use the same Maven property interpolation as dependencies.
final class MavenPluginInterpolationTest {
    @TempDir
    private Path tempDir;

    private final MavenStaticProjectInspector inspector = new MavenStaticProjectInspector();

    @Test
    void interpolatesPluginCoordinatesAndProjectNameBeforeReporting() throws IOException {
        Files.writeString(tempDir.resolve("pom.xml"), """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example.tools</groupId>
                  <artifactId>parser</artifactId>
                  <version>1.0.0</version>
                  <name>${project.groupId}:${project.artifactId}</name>
                  <properties>
                    <antlr.group>org.antlr</antlr.group>
                    <antlr.plugin>antlr4-maven-plugin</antlr.plugin>
                    <antlr.version>4.13.2</antlr.version>
                  </properties>
                  <build>
                    <plugins>
                      <plugin>
                        <groupId>${antlr.group}</groupId>
                        <artifactId>${antlr.plugin}</artifactId>
                        <version>${antlr.version}</version>
                        <executions>
                          <execution>
                            <phase>generate-sources</phase>
                          </execution>
                        </executions>
                      </plugin>
                    </plugins>
                  </build>
                </project>
                """);

        MavenInspectionResult result = inspector.inspect(tempDir);
        MavenProjectInspection project = result.projects().getFirst();
        MavenExplainFormatter formatter = new MavenExplainFormatter();
        String text = formatter.text(result);
        String json = formatter.json(result);

        assertEquals("com.example.tools:parser", project.name());
        assertEquals("org.antlr:antlr4-maven-plugin:4.13.2", project.plugins().getFirst().coordinate());
        assertTrue(text.contains("name: com.example.tools:parser"), () -> text);
        assertTrue(
                text.contains("Plugin `org.antlr:antlr4-maven-plugin:4.13.2` runs in lifecycle phase(s)"),
                () -> text);
        assertTrue(json.contains("\"displayName\": \"com.example.tools:parser\""), () -> json);
        assertTrue(json.contains("\"coordinate\": \"org.antlr:antlr4-maven-plugin:4.13.2\""), () -> json);
        assertTrue(result.signals().stream()
                        .anyMatch(signal -> signal.message().contains("org.antlr:antlr4-maven-plugin:4.13.2")),
                () -> "expected interpolated plugin coordinate in signal messages: " + result.signals());
    }

    @Test
    void unresolvedPluginAndNamePropertiesStayLiteral() throws IOException {
        Files.writeString(tempDir.resolve("pom.xml"), """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example</groupId>
                  <artifactId>demo</artifactId>
                  <version>1.0.0</version>
                  <name>${missing.name}</name>
                  <build>
                    <plugins>
                      <plugin>
                        <groupId>com.example</groupId>
                        <artifactId>custom-plugin</artifactId>
                        <version>${missing.version}</version>
                      </plugin>
                    </plugins>
                  </build>
                </project>
                """);

        MavenProjectInspection project = inspector.inspect(tempDir).projects().getFirst();

        assertEquals("${missing.name}", project.name());
        assertEquals("com.example:custom-plugin:${missing.version}", project.plugins().getFirst().coordinate());
    }
}
