package com.zolt.explain.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.explain.ExplainSignal;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class MavenStaticProjectInspectorTest {
    @TempDir
    private Path tempDir;

    private final MavenStaticProjectInspector inspector = new MavenStaticProjectInspector();

    @Test
    void inspectsSimpleSingleModuleMavenProject() throws IOException {
        Files.writeString(tempDir.resolve("pom.xml"), """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example</groupId>
                  <artifactId>demo</artifactId>
                  <version>1.0.0</version>
                  <properties>
                    <maven.compiler.release>21</maven.compiler.release>
                  </properties>
                  <dependencies>
                    <dependency>
                      <groupId>com.google.guava</groupId>
                      <artifactId>guava</artifactId>
                      <version>33.4.8-jre</version>
                    </dependency>
                    <dependency>
                      <groupId>org.junit.jupiter</groupId>
                      <artifactId>junit-jupiter</artifactId>
                      <version>5.11.4</version>
                      <scope>test</scope>
                    </dependency>
                  </dependencies>
                </project>
                """);

        MavenInspectionResult result = inspector.inspect(tempDir);

        assertEquals(tempDir.toAbsolutePath().normalize(), result.root());
        assertEquals(1, result.projects().size());
        MavenProjectInspection project = result.projects().getFirst();
        assertEquals(Path.of("."), project.path());
        assertEquals("demo", project.artifactId());
        assertEquals("jar", project.packaging());
        assertEquals("21", project.javaVersion());
        assertEquals("src/main/java", project.sourceRoots().getFirst());
        assertEquals("src/test/java", project.testSourceRoots().getFirst());
        assertEquals("src/main/resources", project.resourceRoots().getFirst());
        assertEquals(2, project.dependencies().size());
        assertTrue(project.dependencies().stream()
                .anyMatch(dependency -> dependency.coordinate().equals("com.google.guava:guava:33.4.8-jre")));
        assertTrue(result.signals().isEmpty());
    }

    @Test
    void reportsMultiModuleProjectsAndMissingModulePom() throws IOException {
        Files.writeString(tempDir.resolve("pom.xml"), """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example</groupId>
                  <artifactId>root</artifactId>
                  <version>1.0.0</version>
                  <packaging>pom</packaging>
                  <modules>
                    <module>app</module>
                    <module>missing</module>
                  </modules>
                </project>
                """);
        Path app = tempDir.resolve("app");
        Files.createDirectories(app);
        Files.writeString(app.resolve("pom.xml"), """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <parent>
                    <groupId>com.example</groupId>
                    <artifactId>root</artifactId>
                    <version>1.0.0</version>
                  </parent>
                  <artifactId>app</artifactId>
                </project>
                """);

        MavenInspectionResult result = inspector.inspect(tempDir);

        assertEquals(2, result.projects().size());
        assertEquals(Path.of("."), result.projects().get(0).path());
        assertEquals(Path.of("app"), result.projects().get(1).path());
        assertEquals("app", result.projects().get(1).artifactId());
        assertTrue(result.projects().getFirst().modules().contains("app"));
        assertTrue(result.projects().getFirst().modules().contains("missing"));
        assertTrue(result.signals().stream()
                .anyMatch(signal -> signal.id().equals("maven.module.missing-pom")
                        && signal.severity() == ExplainSignal.Severity.BLOCK));
    }

    @Test
    void reportsProfilesLifecyclePluginsBomsRepositoriesAndDynamicVersions() throws IOException {
        Files.writeString(tempDir.resolve("pom.xml"), """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example</groupId>
                  <artifactId>legacy</artifactId>
                  <version>1.0.0</version>
                  <packaging>war</packaging>
                  <dependencyManagement>
                    <dependencies>
                      <dependency>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-dependencies</artifactId>
                        <version>4.0.6</version>
                        <type>pom</type>
                        <scope>import</scope>
                      </dependency>
                    </dependencies>
                  </dependencyManagement>
                  <dependencies>
                    <dependency>
                      <groupId>com.example</groupId>
                      <artifactId>legacy-lib</artifactId>
                      <version>[1.0,2.0)</version>
                    </dependency>
                  </dependencies>
                  <repositories>
                    <repository>
                      <id>internal</id>
                      <url>https://repo.example.test/maven</url>
                    </repository>
                  </repositories>
                  <build>
                    <sourceDirectory>source/java</sourceDirectory>
                    <testSourceDirectory>test/java</testSourceDirectory>
                    <resources>
                      <resource>
                        <directory>config</directory>
                      </resource>
                    </resources>
                    <plugins>
                      <plugin>
                        <groupId>com.example</groupId>
                        <artifactId>codegen-maven-plugin</artifactId>
                        <version>1.0.0</version>
                        <executions>
                          <execution>
                            <phase>generate-sources</phase>
                          </execution>
                        </executions>
                      </plugin>
                    </plugins>
                  </build>
                  <profiles>
                    <profile>
                      <id>ci</id>
                      <activation>
                        <property>
                          <name>env.CI</name>
                        </property>
                      </activation>
                    </profile>
                  </profiles>
                </project>
                """);

        MavenInspectionResult result = inspector.inspect(tempDir);
        MavenProjectInspection project = result.projects().getFirst();

        assertEquals("war", project.packaging());
        assertEquals("source/java", project.sourceRoots().getFirst());
        assertEquals("test/java", project.testSourceRoots().getFirst());
        assertEquals("config", project.resourceRoots().getFirst());
        assertEquals(1, project.importedBoms().size());
        assertEquals("org.springframework.boot:spring-boot-dependencies:4.0.6",
                project.importedBoms().getFirst().coordinate());
        assertEquals("internal", project.repositories().getFirst().id());
        assertEquals(1, project.plugins().size());
        assertEquals(1, project.profiles().size());
        assertTrue(result.signals().stream()
                .anyMatch(signal -> signal.id().equals("maven.packaging.unsupported")
                        && signal.category() == ExplainSignal.Category.MIGRATION_BLOCKER));
        assertTrue(result.signals().stream()
                .anyMatch(signal -> signal.id().equals("maven.dependency.dynamic-version")
                        && signal.category() == ExplainSignal.Category.NON_DETERMINISM));
        assertTrue(result.signals().stream()
                .anyMatch(signal -> signal.id().equals("maven.plugin.lifecycle-binding")
                        && signal.category() == ExplainSignal.Category.MIGRATION_BLOCKER));
        assertTrue(result.signals().stream()
                .anyMatch(signal -> signal.id().equals("maven.profile.detected")
                        && signal.category() == ExplainSignal.Category.NON_DETERMINISM));
    }

    // : reactor parent inheritance ---------------------------------------------------

    @Test
    void inheritsParentDependencyManagementAndPropertiesIntoChild() throws IOException {
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
                  <properties>
                    <maven.compiler.release>17</maven.compiler.release>
                    <jackson.version>2.17.1</jackson.version>
                  </properties>
                  <dependencyManagement>
                    <dependencies>
                      <dependency>
                        <groupId>com.fasterxml.jackson.core</groupId>
                        <artifactId>jackson-databind</artifactId>
                        <version>${jackson.version}</version>
                      </dependency>
                      <dependency>
                        <groupId>org.junit.jupiter</groupId>
                        <artifactId>junit-jupiter</artifactId>
                        <version>5.11.4</version>
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
                      <groupId>com.fasterxml.jackson.core</groupId>
                      <artifactId>jackson-databind</artifactId>
                    </dependency>
                    <dependency>
                      <groupId>org.junit.jupiter</groupId>
                      <artifactId>junit-jupiter</artifactId>
                      <scope>test</scope>
                    </dependency>
                  </dependencies>
                </project>
                """);

        MavenInspectionResult result = inspector.inspect(tempDir);
        MavenProjectInspection child = childProject(result, "service");

        assertEquals("com.example", child.groupId());
        assertEquals("1.0.0", child.version());
        assertEquals("17", child.javaVersion(), "child inherits the parent maven.compiler.release");
        MavenDependencyInspection jackson = dependency(child, "com.fasterxml.jackson.core:jackson-databind");
        assertEquals("com.fasterxml.jackson.core:jackson-databind:2.17.1", jackson.coordinate());
        assertEquals("2.17.1", jackson.version(), "inherited managed version resolves through a property");
        assertEquals("5.11.4", dependency(child, "org.junit.jupiter:junit-jupiter").version());
        assertFalse(
                child.dependencies().stream().anyMatch(dependency -> dependency.version().isBlank()),
                () -> "no child dependency should remain version-less: " + child.dependencies());
    }

    @Test
    void inheritsAcrossGrandparentChain() throws IOException {
        Files.writeString(tempDir.resolve("pom.xml"), """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example</groupId>
                  <artifactId>platform</artifactId>
                  <version>1.0.0</version>
                  <packaging>pom</packaging>
                  <modules>
                    <module>parent</module>
                  </modules>
                  <properties>
                    <maven.compiler.release>21</maven.compiler.release>
                    <guava.version>33.4.8-jre</guava.version>
                  </properties>
                  <dependencyManagement>
                    <dependencies>
                      <dependency>
                        <groupId>com.google.guava</groupId>
                        <artifactId>guava</artifactId>
                        <version>${guava.version}</version>
                      </dependency>
                    </dependencies>
                  </dependencyManagement>
                </project>
                """);
        Path parent = tempDir.resolve("parent");
        Files.createDirectories(parent);
        Files.writeString(parent.resolve("pom.xml"), """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <parent>
                    <groupId>com.example</groupId>
                    <artifactId>platform</artifactId>
                    <version>1.0.0</version>
                  </parent>
                  <artifactId>parent</artifactId>
                  <packaging>pom</packaging>
                  <modules>
                    <module>leaf</module>
                  </modules>
                </project>
                """);
        Path leaf = parent.resolve("leaf");
        Files.createDirectories(leaf);
        Files.writeString(leaf.resolve("pom.xml"), """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <parent>
                    <groupId>com.example</groupId>
                    <artifactId>parent</artifactId>
                    <version>1.0.0</version>
                  </parent>
                  <artifactId>leaf</artifactId>
                  <dependencies>
                    <dependency>
                      <groupId>com.google.guava</groupId>
                      <artifactId>guava</artifactId>
                    </dependency>
                  </dependencies>
                </project>
                """);

        MavenInspectionResult result = inspector.inspect(tempDir);
        MavenProjectInspection child = childProject(result, "leaf");

        assertEquals("21", child.javaVersion(), "grandchild inherits grandparent java version");
        assertEquals("33.4.8-jre", dependency(child, "com.google.guava:guava").version(),
                "grandchild inherits grandparent managed version through a property");
    }

    @Test
    void childOwnVersionOverridesInheritedManagement() throws IOException {
        Files.writeString(tempDir.resolve("pom.xml"), """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example</groupId>
                  <artifactId>root</artifactId>
                  <version>1.0.0</version>
                  <packaging>pom</packaging>
                  <modules>
                    <module>app</module>
                  </modules>
                  <dependencyManagement>
                    <dependencies>
                      <dependency>
                        <groupId>com.google.guava</groupId>
                        <artifactId>guava</artifactId>
                        <version>32.0.0-jre</version>
                      </dependency>
                    </dependencies>
                  </dependencyManagement>
                </project>
                """);
        Path app = tempDir.resolve("app");
        Files.createDirectories(app);
        Files.writeString(app.resolve("pom.xml"), """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <parent>
                    <groupId>com.example</groupId>
                    <artifactId>root</artifactId>
                    <version>1.0.0</version>
                  </parent>
                  <artifactId>app</artifactId>
                  <dependencies>
                    <dependency>
                      <groupId>com.google.guava</groupId>
                      <artifactId>guava</artifactId>
                      <version>33.4.8-jre</version>
                    </dependency>
                  </dependencies>
                </project>
                """);

        MavenInspectionResult result = inspector.inspect(tempDir);
        MavenProjectInspection child = childProject(result, "app");

        assertEquals("33.4.8-jre", dependency(child, "com.google.guava:guava").version(),
                "an explicit dependency version is not overwritten by inherited management");
    }

    @Test
    void externalParentLeavesChildDependenciesVersionLess() throws IOException {
        // A single module whose <parent> points to a coordinate that is NOT on disk in the reactor.
        // The static audit must not fetch it; the child keeps honest version-less deps and unknown java.
        Files.writeString(tempDir.resolve("pom.xml"), """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <parent>
                    <groupId>org.springframework.boot</groupId>
                    <artifactId>spring-boot-starter-parent</artifactId>
                    <version>3.3.0</version>
                  </parent>
                  <groupId>com.example</groupId>
                  <artifactId>service</artifactId>
                  <version>1.0.0</version>
                  <dependencies>
                    <dependency>
                      <groupId>com.google.guava</groupId>
                      <artifactId>guava</artifactId>
                    </dependency>
                  </dependencies>
                </project>
                """);

        MavenInspectionResult result = inspector.inspect(tempDir);
        MavenProjectInspection project = result.projects().getFirst();

        assertEquals("unknown", project.javaVersion(),
                "an external parent's java version is not fetched over the network");
        assertTrue(dependency(project, "com.google.guava:guava").version().isBlank(),
                "an external parent's managed versions are not fabricated");
        assertEquals("com.google.guava:guava", dependency(project, "com.google.guava:guava").coordinate());
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
