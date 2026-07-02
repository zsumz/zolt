package sh.zolt.explain.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class MavenParentRepositoryInspectionTest {
    @TempDir
    private Path tempDir;

    private final MavenStaticProjectInspector inspector = new MavenStaticProjectInspector();

    @Test
    void reportsExternalSnapshotParentAndRepositoriesInTextJsonAndSignals() throws IOException {
        Files.writeString(tempDir.resolve("pom.xml"), """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <parent>
                    <groupId>com.example.parents</groupId>
                    <artifactId>enterprise-parent</artifactId>
                    <version>1.0.0-SNAPSHOT</version>
                  </parent>
                  <groupId>com.example</groupId>
                  <artifactId>service</artifactId>
                  <version>1.0.0</version>
                  <repositories>
                    <repository>
                      <id>snapshots</id>
                      <url>https://repo.example.test/snapshots</url>
                      <snapshots>
                        <enabled>true</enabled>
                      </snapshots>
                    </repository>
                  </repositories>
                  <pluginRepositories>
                    <pluginRepository>
                      <id>plugins</id>
                      <url>https://repo.example.test/plugins</url>
                    </pluginRepository>
                  </pluginRepositories>
                </project>
                """);

        MavenInspectionResult result = inspector.inspect(tempDir);
        MavenProjectInspection project = result.projects().getFirst();
        String text = new MavenExplainFormatter().text(result);
        String json = new MavenExplainFormatter().json(result);

        assertEquals(1, project.parents().size());
        MavenParentInspection parent = project.parents().getFirst();
        assertEquals("com.example.parents:enterprise-parent:1.0.0-SNAPSHOT", parent.coordinate());
        assertFalse(parent.inReactor());
        assertFalse(parent.resolved());
        assertEquals(2, project.repositories().size());
        assertTrue(project.repositories().stream()
                .anyMatch(repository -> repository.id().equals("snapshots")
                        && !repository.pluginRepository()
                        && repository.snapshotsEnabled()));
        assertTrue(project.repositories().stream()
                .anyMatch(repository -> repository.id().equals("plugins")
                        && repository.pluginRepository()
                        && !repository.snapshotsEnabled()));
        assertTrue(result.signals().stream().anyMatch(signal -> signal.id().equals("maven.parent.snapshot")));
        assertTrue(result.signals().stream().anyMatch(signal -> signal.id().equals("maven.repository.declared")));
        assertTrue(result.signals().stream().anyMatch(signal -> signal.id().equals("maven.repository.snapshots-enabled")));
        assertTrue(text.contains("parents: 1"), () -> text);
        assertTrue(text.contains("com.example.parents:enterprise-parent:1.0.0-SNAPSHOT (external/unresolved)"), () -> text);
        assertTrue(text.contains("repositories: 2"), () -> text);
        assertTrue(text.contains("pluginRepository plugins https://repo.example.test/plugins"), () -> text);
        assertTrue(json.contains("\"parents\": ["), () -> json);
        assertTrue(json.contains("\"coordinate\": \"com.example.parents:enterprise-parent:1.0.0-SNAPSHOT\""), () -> json);
        assertTrue(json.contains("\"repositories\": ["), () -> json);
        assertTrue(json.contains("\"snapshotsEnabled\": true"), () -> json);
    }

    @Test
    void rootAggregatorInheritsFromParentPomInSubdirectoryModule() throws IOException {
        Files.writeString(tempDir.resolve("pom.xml"), """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <parent>
                    <groupId>com.example</groupId>
                    <artifactId>platform-parent</artifactId>
                    <version>1.0.0</version>
                    <relativePath>parent/pom.xml</relativePath>
                  </parent>
                  <artifactId>root</artifactId>
                  <packaging>pom</packaging>
                  <modules>
                    <module>parent</module>
                    <module>app</module>
                  </modules>
                </project>
                """);
        Path parent = tempDir.resolve("parent");
        Files.createDirectories(parent);
        Files.writeString(parent.resolve("pom.xml"), """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example</groupId>
                  <artifactId>platform-parent</artifactId>
                  <version>1.0.0</version>
                  <packaging>pom</packaging>
                  <properties>
                    <maven.compiler.release>1.8</maven.compiler.release>
                  </properties>
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
        MavenProjectInspection root = childProject(result, "root");

        assertEquals("1.8", root.javaVersion(), "root inherits java from its subdirectory parent POM");
        assertEquals(1, root.parents().size());
        assertEquals("com.example:platform-parent:1.0.0", root.parents().getFirst().coordinate());
        assertTrue(root.parents().getFirst().inReactor());
        assertEquals("parent", root.parents().getFirst().path());
    }

    private static MavenProjectInspection childProject(MavenInspectionResult result, String artifactId) {
        return result.projects().stream()
                .filter(project -> project.artifactId().equals(artifactId))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no module named " + artifactId + " in " + result.projects()));
    }
}
