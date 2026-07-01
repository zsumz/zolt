package com.zolt.explain.emit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.explain.maven.MavenInspectionResult;
import com.zolt.explain.maven.MavenStaticProjectInspector;
import com.zolt.project.ProjectConfig;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** : a multi-module Maven reactor emits a Zolt workspace, not just the root. */
final class InspectionToWorkspaceMavenTest {
    @TempDir
    private Path tempDir;

    private final InspectionToProjectConfig mapper = new InspectionToProjectConfig();

    private DraftWorkspace emitReactor() throws IOException {
        Files.writeString(tempDir.resolve("pom.xml"), """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.acme.shop</groupId>
                  <artifactId>shop-parent</artifactId>
                  <version>1.4.0</version>
                  <packaging>pom</packaging>
                  <properties>
                    <maven.compiler.release>21</maven.compiler.release>
                    <jackson.version>2.17.1</jackson.version>
                  </properties>
                  <modules>
                    <module>orders-core</module>
                    <module>orders-api</module>
                  </modules>
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
        writeModule("orders-core", """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <parent>
                    <groupId>com.acme.shop</groupId>
                    <artifactId>shop-parent</artifactId>
                    <version>1.4.0</version>
                  </parent>
                  <artifactId>orders-core</artifactId>
                  <dependencies>
                    <dependency>
                      <groupId>com.fasterxml.jackson.core</groupId>
                      <artifactId>jackson-databind</artifactId>
                    </dependency>
                  </dependencies>
                </project>
                """);
        writeModule("orders-api", """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <parent>
                    <groupId>com.acme.shop</groupId>
                    <artifactId>shop-parent</artifactId>
                    <version>1.4.0</version>
                  </parent>
                  <artifactId>orders-api</artifactId>
                  <dependencies>
                    <dependency>
                      <groupId>com.acme.shop</groupId>
                      <artifactId>orders-core</artifactId>
                      <version>${project.version}</version>
                    </dependency>
                    <dependency>
                      <groupId>org.junit.jupiter</groupId>
                      <artifactId>junit-jupiter</artifactId>
                      <scope>test</scope>
                    </dependency>
                  </dependencies>
                </project>
                """);
        MavenInspectionResult result = new MavenStaticProjectInspector().inspect(tempDir);
        DraftEmit emit = mapper.emitFromMaven(result);
        return assertInstanceOf(DraftWorkspace.class, emit, () -> "reactor must emit a workspace, got " + emit);
    }

    private void writeModule(String name, String pom) throws IOException {
        Path dir = tempDir.resolve(name);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("pom.xml"), pom);
    }

    @Test
    void reactorEmitsWorkspaceRootWithMembersAndDefaultMembers() throws IOException {
        DraftWorkspace workspace = emitReactor();

        assertEquals("shop-parent", workspace.workspace().name());
        assertEquals(List.of("orders-api", "orders-core"), workspace.workspace().members());
        assertEquals(List.of("orders-api", "orders-core"), workspace.workspace().defaultMembers());
        assertEquals(2, workspace.members().size());
    }

    @Test
    void membersInheritConcreteParentManagedVersions() throws IOException {
        DraftWorkspace workspace = emitReactor();

        ProjectConfig core = member(workspace, "orders-core");
        assertEquals("21", core.project().java(), "member inherits the parent Java version");
        assertEquals("1.4.0", core.project().version());
        assertEquals("com.acme.shop", core.project().group());
        assertEquals(
                "2.17.1",
                core.dependencies().get("com.fasterxml.jackson.core:jackson-databind"),
                () -> "member must carry the inherited concrete version: " + core.dependencies());
    }

    @Test
    void interModuleDependencyBecomesWorkspaceEdgeNotExternalCoordinate() throws IOException {
        DraftWorkspace workspace = emitReactor();

        ProjectConfig api = member(workspace, "orders-api");
        Map<String, String> workspaceDeps = api.workspaceDependencies();
        assertEquals(
                "orders-core",
                workspaceDeps.get("com.acme.shop:orders-core"),
                () -> "sibling dep must be a { workspace = ... } edge: " + workspaceDeps);
        assertFalse(
                api.dependencies().containsKey("com.acme.shop:orders-core"),
                () -> "sibling dep must not be emitted as an external coordinate: " + api.dependencies());
        assertEquals(
                "5.11.4",
                api.testDependencies().get("org.junit.jupiter:junit-jupiter"),
                () -> "external test dep still resolves to a concrete version: " + api.testDependencies());
    }

    @Test
    void rootAggregatorWithNoDependenciesAddsNoDependencyNote() throws IOException {
        DraftWorkspace workspace = emitReactor();

        assertTrue(
                workspace.notes().isEmpty(),
                () -> "a pom aggregator with no deps needs no workspace note: " + workspace.notes());
    }

    private static ProjectConfig member(DraftWorkspace workspace, String path) {
        return workspace.members().stream()
                .filter(member -> member.path().equals(path))
                .map(member -> member.draft().config())
                .findFirst()
                .orElseThrow(() -> new AssertionError("no member at " + path + " in " + workspace.members()));
    }
}
