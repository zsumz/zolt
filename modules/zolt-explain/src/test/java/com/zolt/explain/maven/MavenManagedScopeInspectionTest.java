package com.zolt.explain.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class MavenManagedScopeInspectionTest {
    @TempDir
    private Path tempDir;

    private final MavenStaticProjectInspector inspector = new MavenStaticProjectInspector();

    @Test
    void appliesManagedScopeToScopeLessSinglePomDependency() throws IOException {
        Files.writeString(tempDir.resolve("pom.xml"), """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example</groupId>
                  <artifactId>demo</artifactId>
                  <version>1.0.0</version>
                  <dependencyManagement>
                    <dependencies>
                      <dependency>
                        <groupId>org.mockito</groupId>
                        <artifactId>mockito-inline</artifactId>
                        <version>4.11.0</version>
                        <scope>test</scope>
                      </dependency>
                    </dependencies>
                  </dependencyManagement>
                  <dependencies>
                    <dependency>
                      <groupId>org.mockito</groupId>
                      <artifactId>mockito-inline</artifactId>
                    </dependency>
                  </dependencies>
                </project>
                """);

        MavenInspectionResult result = inspector.inspect(tempDir);
        MavenDependencyInspection dependency =
                dependency(result.projects().getFirst(), "org.mockito:mockito-inline");
        String text = new MavenExplainFormatter().text(result);
        String json = new MavenExplainFormatter().json(result);

        assertEquals("4.11.0", dependency.version());
        assertEquals("test", dependency.scope());
        assertEquals("org.mockito:mockito-inline:4.11.0", dependency.coordinate());
        assertTrue(text.contains("- test org.mockito:mockito-inline:4.11.0"), () -> text);
        assertTrue(json.contains("\"scope\": \"test\""), () -> json);
        assertTrue(json.contains("\"coordinate\": \"org.mockito:mockito-inline:4.11.0\""), () -> json);
        assertFalse(json.contains("scopeDeclared"), () -> "schema should not expose parser internals:\n" + json);
    }

    @Test
    void explicitLocalScopeOverridesManagedScope() throws IOException {
        Files.writeString(tempDir.resolve("pom.xml"), """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example</groupId>
                  <artifactId>demo</artifactId>
                  <version>1.0.0</version>
                  <dependencyManagement>
                    <dependencies>
                      <dependency>
                        <groupId>com.google.guava</groupId>
                        <artifactId>guava</artifactId>
                        <version>33.4.8-jre</version>
                        <scope>test</scope>
                      </dependency>
                    </dependencies>
                  </dependencyManagement>
                  <dependencies>
                    <dependency>
                      <groupId>com.google.guava</groupId>
                      <artifactId>guava</artifactId>
                      <scope>compile</scope>
                    </dependency>
                  </dependencies>
                </project>
                """);

        MavenDependencyInspection dependency =
                dependency(inspector.inspect(tempDir).projects().getFirst(), "com.google.guava:guava");

        assertEquals("33.4.8-jre", dependency.version(), "managed version still applies");
        assertEquals("compile", dependency.scope(), "explicit local compile scope must win over managed test");
    }

    @Test
    void managedScopeAppliesWhenLocalVersionIsExplicit() throws IOException {
        Files.writeString(tempDir.resolve("pom.xml"), """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example</groupId>
                  <artifactId>demo</artifactId>
                  <version>1.0.0</version>
                  <dependencyManagement>
                    <dependencies>
                      <dependency>
                        <groupId>org.mockito</groupId>
                        <artifactId>mockito-inline</artifactId>
                        <version>4.10.0</version>
                        <scope>test</scope>
                      </dependency>
                    </dependencies>
                  </dependencyManagement>
                  <dependencies>
                    <dependency>
                      <groupId>org.mockito</groupId>
                      <artifactId>mockito-inline</artifactId>
                      <version>4.11.0</version>
                    </dependency>
                  </dependencies>
                </project>
                """);

        MavenDependencyInspection dependency =
                dependency(inspector.inspect(tempDir).projects().getFirst(), "org.mockito:mockito-inline");

        assertEquals("4.11.0", dependency.version(), "explicit local version must win over managed version");
        assertEquals("test", dependency.scope(), "managed scope still applies when local scope is omitted");
        assertEquals("org.mockito:mockito-inline:4.11.0", dependency.coordinate());
    }

    @Test
    void inheritsManagedScopeFromReactorParent() throws IOException {
        Files.writeString(tempDir.resolve("pom.xml"), """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example</groupId>
                  <artifactId>root</artifactId>
                  <version>1.0.0</version>
                  <packaging>pom</packaging>
                  <modules>
                    <module>service</module>
                  </modules>
                  <dependencyManagement>
                    <dependencies>
                      <dependency>
                        <groupId>org.hamcrest</groupId>
                        <artifactId>hamcrest</artifactId>
                        <version>3.0</version>
                        <scope>test</scope>
                      </dependency>
                    </dependencies>
                  </dependencyManagement>
                </project>
                """);
        Path service = tempDir.resolve("service");
        Files.createDirectories(service);
        Files.writeString(service.resolve("pom.xml"), """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <parent>
                    <groupId>com.example</groupId>
                    <artifactId>root</artifactId>
                    <version>1.0.0</version>
                  </parent>
                  <artifactId>service</artifactId>
                  <dependencies>
                    <dependency>
                      <groupId>org.hamcrest</groupId>
                      <artifactId>hamcrest</artifactId>
                    </dependency>
                  </dependencies>
                </project>
                """);

        MavenProjectInspection child = childProject(inspector.inspect(tempDir), "service");
        MavenDependencyInspection dependency = dependency(child, "org.hamcrest:hamcrest");

        assertEquals("3.0", dependency.version());
        assertEquals("test", dependency.scope(), "child inherits managed scope from its reactor parent");
    }

    private static MavenProjectInspection childProject(MavenInspectionResult result, String artifactId) {
        return result.projects().stream()
                .filter(project -> project.artifactId().equals(artifactId))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no module named " + artifactId + " in " + result.projects()));
    }

    private static MavenDependencyInspection dependency(MavenProjectInspection project, String groupArtifact) {
        return project.dependencies().stream()
                .filter(dependency -> {
                    String[] parts = dependency.coordinate().split(":");
                    return parts.length >= 2 && (parts[0] + ":" + parts[1]).equals(groupArtifact);
                })
                .findFirst()
                .orElseThrow(() ->
                        new AssertionError("no dependency " + groupArtifact + " in " + project.dependencies()));
    }
}
