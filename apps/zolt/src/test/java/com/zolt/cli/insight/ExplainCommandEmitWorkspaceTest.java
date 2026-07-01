package com.zolt.cli.insight;

import static com.zolt.cli.CliTestSupport.execute;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.cli.CliTestSupport.CommandResult;
import com.zolt.project.ProjectConfig;
import com.zolt.toml.ZoltTomlParser;
import com.zolt.workspace.WorkspaceConfig;
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
                dependencies {
                    implementation libs.guava
                    implementation project(':core')
                    testImplementation libs.junit.jupiter
                }
                """);
        writeGradleModule("core", """
                plugins { id 'java-library' }
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
        assertTrue(toml.contains("\"core\" = { workspace = \"core\" }"), () -> toml);
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
        String edge = app.workspaceDependencies().get("core");
        assertEquals("core", edge);
        assertTrue(workspace.members().contains(edge), () -> "edge target must be a member: " + edge);

        ProjectConfig core = new ZoltTomlParser().parse(documents.get("core"));
        assertEquals("3.17.0", core.apiDependencies().get("org.apache.commons:commons-lang3"));
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
}
