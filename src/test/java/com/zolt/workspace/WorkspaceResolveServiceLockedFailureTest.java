package com.zolt.workspace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.resolve.ResolveException;
import com.zolt.resolve.ResolveResult;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class WorkspaceResolveServiceLockedFailureTest {
    private final WorkspaceResolveService service = new WorkspaceResolveService();
    private final Map<String, byte[]> responses = new HashMap<>();

    @TempDir
    private Path tempDir;

    private com.sun.net.httpserver.HttpServer server;
    private URI baseUri;

    @BeforeEach
    void startServer() throws IOException {
        server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.start();
        baseUri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/maven2/");
        addArtifact("com.example", "app", "1.0.0", """
                <project>
                  <groupId>com.example</groupId>
                  <artifactId>app</artifactId>
                  <version>1.0.0</version>
                  <dependencies>
                    <dependency>
                      <groupId>com.example</groupId>
                      <artifactId>lib</artifactId>
                      <version>1.0.0</version>
                    </dependency>
                  </dependencies>
                </project>
                """);
        addArtifact("com.example", "lib", "1.0.0", pom("com.example", "lib", "1.0.0"));
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void lockedWorkspaceResolveFailsWhenRootLockfileWouldChange() throws IOException {
        addArtifact("com.example", "extra", "1.0.0", pom("com.example", "extra", "1.0.0"));
        workspace("""
                [workspace]
                name = "acme-platform"
                members = ["apps/api"]

                [repositories]
                test = "%s"
                """.formatted(baseUri));
        member("apps/api", "api", """

                [dependencies]
                "com.example:app" = "1.0.0"
                """);
        ResolveResult first = service.resolve(tempDir, tempDir.resolve("cache"), false, false);
        String existing = Files.readString(first.lockfilePath());
        member("apps/api", "api", """

                [dependencies]
                "com.example:app" = "1.0.0"
                "com.example:extra" = "1.0.0"
                """);

        ResolveException exception = assertThrows(
                ResolveException.class,
                () -> service.resolve(tempDir, tempDir.resolve("cache"), true, false));

        assertTrue(exception.getMessage().contains("Workspace zolt.lock is out of date"));
        assertEquals(existing, Files.readString(first.lockfilePath()));
    }

    @Test
    void lockedWorkspaceResolveFailsWhenRepositoryInputChangesWithoutGraphChange() throws IOException {
        workspace("""
                [workspace]
                name = "acme-platform"
                members = ["apps/api"]

                [repositories]
                test = "%s"
                """.formatted(baseUri));
        member("apps/api", "api", """

                [dependencies]
                "com.example:app" = "1.0.0"
                """);
        ResolveResult first = service.resolve(tempDir, tempDir.resolve("cache"), false, false);
        String existing = Files.readString(first.lockfilePath());
        workspace("""
                [workspace]
                name = "acme-platform"
                members = ["apps/api"]

                [repositories]
                test = "%s?changed=true"
                """.formatted(baseUri));

        ResolveException exception = assertThrows(
                ResolveException.class,
                () -> service.resolve(tempDir, tempDir.resolve("cache"), true, false));

        assertTrue(existing.contains("projectResolutionFingerprint = \"sha256:"));
        assertTrue(exception.getMessage().contains("Workspace zolt.lock is out of date"));
        assertTrue(exception.getMessage().contains("Changed inputs: repositories."));
        assertEquals(existing, Files.readString(first.lockfilePath()));
    }

    @Test
    void lockedWorkspaceResolveFailsWhenMemberPlatformVersionRefEdgeChangesWithoutConcreteVersionChange()
            throws IOException {
        addPom("com.example", "platform", "1.0.0", """
                <project>
                  <groupId>com.example</groupId>
                  <artifactId>platform</artifactId>
                  <version>1.0.0</version>
                  <dependencyManagement>
                    <dependencies>
                      <dependency>
                        <groupId>com.example</groupId>
                        <artifactId>app</artifactId>
                        <version>1.0.0</version>
                      </dependency>
                    </dependencies>
                  </dependencyManagement>
                </project>
                """);
        workspace("""
                [workspace]
                name = "acme-platform"
                members = ["apps/api"]

                [repositories]
                test = "%s"
                """.formatted(baseUri));
        platformVersionRefMember("platform-one");
        ResolveResult first = service.resolve(tempDir, tempDir.resolve("cache"), false, false);
        String existing = Files.readString(first.lockfilePath());
        platformVersionRefMember("platform-two");

        ResolveException exception = assertThrows(
                ResolveException.class,
                () -> service.resolve(tempDir, tempDir.resolve("cache"), true, false));

        assertTrue(existing.contains("aliasFingerprint = \"sha256:"));
        assertTrue(exception.getMessage().contains("Workspace zolt.lock is out of date"));
        assertEquals(existing, Files.readString(first.lockfilePath()));
    }

    @Test
    void lockedWorkspaceResolveFailsWhenMemberVersionAliasTableChangesWithoutGraphChange()
            throws IOException {
        workspace("""
                [workspace]
                name = "acme-platform"
                members = ["apps/api"]
                """);
        unusedAliasMember("unused-one");
        ResolveResult first = service.resolve(tempDir, tempDir.resolve("cache"), false, false);
        String existing = Files.readString(first.lockfilePath());
        unusedAliasMember("unused-two");

        ResolveException exception = assertThrows(
                ResolveException.class,
                () -> service.resolve(tempDir, tempDir.resolve("cache"), true, false));

        assertTrue(existing.contains("aliasFingerprint = \"sha256:"));
        assertTrue(exception.getMessage().contains("Workspace zolt.lock is out of date"));
        assertEquals(existing, Files.readString(first.lockfilePath()));
    }

    private void workspace(String content) throws IOException {
        Files.writeString(tempDir.resolve("zolt-workspace.toml"), content);
    }

    private void member(String path, String name, String extraToml) throws IOException {
        Path member = tempDir.resolve(path);
        Files.createDirectories(member);
        Files.writeString(member.resolve("zolt.toml"), """
                [project]
                name = "%s"
                version = "0.1.0"
                group = "com.acme"
                java = "21"
                %s""".formatted(name, extraToml));
    }

    private void platformVersionRefMember(String alias) throws IOException {
        member("apps/api", "api", """

                [versions]
                "%s" = "1.0.0"

                [platforms]
                "com.example:platform" = { versionRef = "%s" }

                [dependencies]
                "com.example:app" = {}
                """.formatted(alias, alias));
    }

    private void unusedAliasMember(String alias) throws IOException {
        member("apps/api", "api", """

                [versions]
                "%s" = "1.0.0"
                """.formatted(alias));
    }

    private void addArtifact(String groupId, String artifactId, String version, String pom) {
        String base = "/maven2/" + groupId.replace('.', '/') + "/" + artifactId + "/" + version + "/" + artifactId + "-" + version;
        responses.put(base + ".pom", pom.getBytes(StandardCharsets.UTF_8));
        responses.put(base + ".jar", new byte[] {0x50, 0x4b, 0x03, 0x04});
    }

    private void addPom(String groupId, String artifactId, String version, String pom) {
        String base = "/maven2/" + groupId.replace('.', '/') + "/" + artifactId + "/" + version + "/" + artifactId + "-" + version;
        responses.put(base + ".pom", pom.getBytes(StandardCharsets.UTF_8));
    }

    private static String pom(String groupId, String artifactId, String version) {
        return """
                <project>
                  <groupId>%s</groupId>
                  <artifactId>%s</artifactId>
                  <version>%s</version>
                </project>
                """.formatted(groupId, artifactId, version);
    }

    private void handle(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
        byte[] body = responses.get(exchange.getRequestURI().getPath());
        if (body == null) {
            respond(exchange, 404, "missing".getBytes(StandardCharsets.UTF_8));
            return;
        }
        respond(exchange, 200, body);
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, int statusCode, byte[] body) throws IOException {
        try (exchange) {
            exchange.sendResponseHeaders(statusCode, body.length);
            exchange.getResponseBody().write(body);
        }
    }
}
