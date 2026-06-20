package com.zolt.explain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        assertEquals("demo", project.name());
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
        assertEquals("app", result.projects().get(1).name());
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

}
