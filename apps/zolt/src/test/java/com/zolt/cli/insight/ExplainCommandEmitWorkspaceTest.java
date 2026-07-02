package com.zolt.cli.insight;

import static com.zolt.cli.CliTestSupport.execute;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.cli.CliTestSupport.CommandResult;
import com.zolt.project.ProjectConfig;
import com.zolt.toml.ZoltTomlParser;
import com.zolt.workspace.WorkspaceConfig;
import com.zolt.workspace.discovery.WorkspaceDiscoveryService;
import com.zolt.workspace.service.Workspace;
import com.zolt.workspace.service.WorkspaceProjectEdge;
import com.zolt.workspace.toml.WorkspaceConfigParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** : `zolt explain --emit-toml` migrates a multi-module build to a Zolt workspace. */
final class ExplainCommandEmitWorkspaceTest {
    @TempDir
    private Path tempDir;

    // --- Maven reactor -----------------------------------------------------------------------

    private void writeMavenReactor() throws IOException {
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
                    <junit.version>5.10.2</junit.version>
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
                        <groupId>org.junit</groupId>
                        <artifactId>junit-bom</artifactId>
                        <version>${junit.version}</version>
                        <type>pom</type>
                        <scope>import</scope>
                      </dependency>
                    </dependencies>
                  </dependencyManagement>
                </project>
                """);
        writeMavenModule("orders-core", """
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
                    <dependency>
                      <groupId>org.junit.jupiter</groupId>
                      <artifactId>junit-jupiter</artifactId>
                      <scope>test</scope>
                    </dependency>
                  </dependencies>
                </project>
                """);
        writeMavenModule("orders-api", """
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
                  </dependencies>
                </project>
                """);
    }

    private void writeMavenModule(String name, String pom) throws IOException {
        Path dir = tempDir.resolve(name);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("pom.xml"), pom);
    }

    @Test
    void mavenReactorEmitsWorkspaceBundleWithLabelledMembersAndEdge() throws IOException {
        writeMavenReactor();

        CommandResult result = execute("explain", "--emit-toml", "--cwd", tempDir.toString(), "--source", "maven");

        assertEquals(0, result.exitCode(), () -> result.stderr());
        String toml = result.stdout();
        assertTrue(toml.contains("[workspace]"), () -> toml);
        assertTrue(toml.contains("name = \"shop-parent\""), () -> toml);
        assertTrue(toml.contains("members = [\"orders-api\", \"orders-core\"]"), () -> toml);
        assertTrue(toml.contains("# --- orders-api/zolt.toml ---"), () -> toml);
        assertTrue(toml.contains("# --- orders-core/zolt.toml ---"), () -> toml);
        // The inter-module edge is a workspace dep, not an external coordinate.
        assertTrue(toml.contains("\"com.acme.shop:orders-core\" = { workspace = \"orders-core\" }"), () -> toml);
        // The external dep carries the inherited concrete version.
        assertTrue(toml.contains("\"com.fasterxml.jackson.core:jackson-databind\" = \"2.17.1\""), () -> toml);
        // The child module also carries the parent's imported BOM and platform-managed test dependency.
        assertTrue(toml.contains("\"org.junit:junit-bom\" = \"5.10.2\""), () -> toml);
        assertTrue(toml.contains("\"org.junit.jupiter:junit-jupiter\" = {}"), () -> toml);
        assertFalse(toml.contains("${"), () -> "no interpolation token should survive:\n" + toml);
    }

    @Test
    void mavenWorkspaceBundleRoundTripsThroughParsers() throws IOException {
        writeMavenReactor();

        CommandResult result = execute("explain", "--emit-toml", "--cwd", tempDir.toString(), "--source", "maven");
        assertEquals(0, result.exitCode(), () -> result.stderr());

        Map<String, String> documents = splitDocuments(result.stdout());
        WorkspaceConfig workspace = new WorkspaceConfigParser().parse(documents.get("workspace"));
        assertEquals("shop-parent", workspace.name());
        assertEquals(List.of("orders-api", "orders-core"), workspace.members());

        for (String member : workspace.members()) {
            ProjectConfig parsed = new ZoltTomlParser().parse(documents.get(member));
            assertEquals("com.acme.shop", parsed.project().group(), () -> "member " + member);
        }

        ProjectConfig api = new ZoltTomlParser().parse(documents.get("orders-api"));
        String edge = api.workspaceDependencies().get("com.acme.shop:orders-core");
        assertEquals("orders-core", edge, () -> "workspace edge must point at a real member: " + api.workspaceDependencies());
        assertTrue(workspace.members().contains(edge), () -> "edge target must be a member: " + edge);

        ProjectConfig core = new ZoltTomlParser().parse(documents.get("orders-core"));
        assertEquals("5.10.2", core.platforms().get("org.junit:junit-bom"));
        assertTrue(core.managedTestDependencies().contains("org.junit.jupiter:junit-jupiter"),
                () -> "parent BOM-managed test dep should be emitted as {}: " + core.managedTestDependencies());
    }

    // --- Gradle multi-project ----------------------------------------------------------------

    private void writeGradleMultiProject() throws IOException {
        Files.writeString(tempDir.resolve("settings.gradle"), """
                rootProject.name = 'sales'
                include 'app', 'core'
                """);
        Files.writeString(tempDir.resolve("build.gradle"), "plugins { id 'java' }\n");
        Files.createDirectories(tempDir.resolve("gradle"));
        Files.writeString(tempDir.resolve("gradle/libs.versions.toml"), """
                [versions]
                guava = "33.4.8-jre"
                junit = "5.11.4"
                commonsLang = "3.17.0"

                [libraries]
                guava = { module = "com.google.guava:guava", version.ref = "guava" }
                junit-jupiter = { group = "org.junit.jupiter", name = "junit-jupiter", version.ref = "junit" }
                commons-lang3 = { module = "org.apache.commons:commons-lang3", version.ref = "commonsLang" }
                """);
        writeGradleModule("app", """
                plugins {
                    id 'java'
                    id 'application'
                }
                sourceCompatibility = JavaVersion.VERSION_21
                dependencies {
                    implementation libs.guava
                    implementation project(':core')
                    testImplementation libs.junit.jupiter
                }
                """);
        writeGradleModule("core", """
                plugins { id 'java-library' }
                sourceCompatibility = JavaVersion.VERSION_21
                dependencies {
                    api libs.commons.lang3
                }
                """);
    }

    private void writeGradleModule(String name, String buildGradle) throws IOException {
        Path dir = tempDir.resolve(name);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("build.gradle"), buildGradle);
    }

    @Test
    void gradleMultiProjectEmitsWorkspaceBundleWithEdgeAndCatalogVersions() throws IOException {
        writeGradleMultiProject();

        CommandResult result = execute("explain", "--emit-toml", "--cwd", tempDir.toString(), "--source", "gradle");

        assertEquals(0, result.exitCode(), () -> result.stderr());
        String toml = result.stdout();
        assertTrue(toml.contains("[workspace]"), () -> toml);
        assertTrue(toml.contains("name = \"sales\""), () -> toml);
        assertTrue(toml.contains("members = [\"app\", \"core\"]"), () -> toml);
        assertTrue(toml.contains("# --- app/zolt.toml ---"), () -> toml);
        assertTrue(toml.contains("# --- core/zolt.toml ---"), () -> toml);
        assertTrue(toml.contains("\"com.example:core\" = { workspace = \"core\" }"), () -> toml);
        assertTrue(toml.contains("\"com.google.guava:guava\" = \"33.4.8-jre\""), () -> toml);
        assertTrue(toml.contains("\"org.apache.commons:commons-lang3\" = \"3.17.0\""), () -> toml);
    }

    @Test
    void gradleWorkspaceBundleRoundTripsThroughParsers() throws IOException {
        writeGradleMultiProject();

        CommandResult result = execute("explain", "--emit-toml", "--cwd", tempDir.toString(), "--source", "gradle");
        assertEquals(0, result.exitCode(), () -> result.stderr());

        Map<String, String> documents = splitDocuments(result.stdout());
        WorkspaceConfig workspace = new WorkspaceConfigParser().parse(documents.get("workspace"));
        assertEquals("sales", workspace.name());
        assertEquals(List.of("app", "core"), workspace.members());

        ProjectConfig app = new ZoltTomlParser().parse(documents.get("app"));
        assertEquals("33.4.8-jre", app.dependencies().get("com.google.guava:guava"));
        String edge = app.workspaceDependencies().get("com.example:core");
        assertEquals("core", edge);
        assertTrue(workspace.members().contains(edge), () -> "edge target must be a member: " + edge);

        ProjectConfig core = new ZoltTomlParser().parse(documents.get("core"));
        assertEquals("3.17.0", core.apiDependencies().get("org.apache.commons:commons-lang3"));

        writeDocuments(tempDir, documents);
        Workspace discovered = new WorkspaceDiscoveryService().load(tempDir);
        assertTrue(discovered.edges().stream().anyMatch(ExplainCommandEmitWorkspaceTest::isGradleCoreEdge),
                () -> "emitted Gradle workspace must discover without coordinate mismatch: "
                        + discovered.edges());
    }

    @Test
    void gradleWorkspaceBundleResolvesWhenSplitOut() throws IOException {
        Files.writeString(tempDir.resolve("settings.gradle"), """
                rootProject.name = 'sales'
                include 'app', 'core'
                """);
        Files.writeString(tempDir.resolve("build.gradle"), "plugins { id 'java' }\n");
        writeGradleModule("app", """
                plugins { id 'java-library' }
                sourceCompatibility = JavaVersion.VERSION_21
                dependencies {
                    implementation project(':core')
                }
                """);
        writeGradleModule("core", """
                plugins { id 'java-library' }
                sourceCompatibility = JavaVersion.VERSION_21
                """);

        CommandResult explain = execute("explain", "--emit-toml", "--cwd", tempDir.toString(), "--source", "gradle");
        assertEquals(0, explain.exitCode(), () -> explain.stderr());
        writeDocuments(tempDir, splitDocuments(explain.stdout()));

        CommandResult resolve = execute(
                "resolve",
                "--workspace",
                "--cwd",
                tempDir.toString(),
                "--cache-root",
                tempDir.resolve("cache").toString());
        assertEquals(0, resolve.exitCode(), () -> resolve.stdout() + resolve.stderr());
    }

    // --- helpers -----------------------------------------------------------------------------

    /**
     * Splits the emitted multi-document bundle keyed by member path (or "workspace" for the root),
     * dropping the leading comment notice on each document so it parses on its own.
     */
    private static Map<String, String> splitDocuments(String bundle) {
        Map<String, String> documents = new LinkedHashMap<>();
        String key = null;
        StringBuilder body = new StringBuilder();
        for (String line : bundle.split("\n", -1)) {
            String header = documentKey(line);
            if (header != null) {
                if (key != null) {
                    documents.put(key, body.toString());
                }
                key = header;
                body = new StringBuilder();
                continue;
            }
            if (key != null) {
                body.append(line).append('\n');
            }
        }
        if (key != null) {
            documents.put(key, body.toString());
        }
        return documents;
    }

    private static void writeDocuments(Path root, Map<String, String> documents) throws IOException {
        Files.writeString(root.resolve("zolt.toml"), documents.get("workspace"));
        for (Map.Entry<String, String> document : documents.entrySet()) {
            if ("workspace".equals(document.getKey())) {
                continue;
            }
            Path member = root.resolve(document.getKey());
            Files.createDirectories(member);
            Files.writeString(member.resolve("zolt.toml"), document.getValue());
        }
    }

    private static String documentKey(String line) {
        String trimmed = line.strip();
        if (!trimmed.startsWith("# ---") || !trimmed.endsWith("---")) {
            return null;
        }
        if (trimmed.contains("workspace root")) {
            return "workspace";
        }
        String inner = trimmed.substring("# ---".length(), trimmed.length() - "---".length()).strip();
        return inner.endsWith("/zolt.toml") ? inner.substring(0, inner.length() - "/zolt.toml".length()) : inner;
    }

    private static boolean isGradleCoreEdge(WorkspaceProjectEdge edge) {
        return edge.from().equals("app")
                && edge.to().equals("core")
                && edge.coordinate().equals("com.example:core");
    }
}
