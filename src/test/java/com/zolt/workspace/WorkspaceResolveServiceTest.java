package com.zolt.workspace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.zolt.lockfile.ZoltLockfile;
import com.zolt.lockfile.ZoltLockfileReader;
import com.zolt.resolve.DependencyScope;
import com.zolt.resolve.PackageId;
import com.zolt.resolve.ResolveException;
import com.zolt.resolve.ResolveResult;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class WorkspaceResolveServiceTest {
    private final WorkspaceResolveService service = new WorkspaceResolveService();
    private final ZoltLockfileReader lockfileReader = new ZoltLockfileReader();
    private final Map<String, byte[]> responses = new HashMap<>();

    @TempDir
    private Path tempDir;

    private HttpServer server;
    private URI baseUri;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
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
    void resolvesWorkspaceMembersIntoRootLockfile() throws IOException {
        workspace("""
                [workspace]
                name = "acme-platform"
                members = ["apps/api", "modules/core"]

                [repositories]
                test = "%s"
                """.formatted(baseUri));
        member("apps/api", "api", """

                [dependencies]
                "com.acme:core" = { workspace = "modules/core" }
                "com.example:app" = "1.0.0"
                """);
        member("modules/core", "core", """

                [dependencies]
                "com.example:lib" = "1.0.0"
                """);

        ResolveResult result = service.resolve(tempDir.resolve("apps/api"), tempDir.resolve("cache"), false, false);

        assertEquals(2, result.resolvedCount());
        assertEquals(4, result.downloadCount());
        assertEquals(0, result.conflictCount());
        assertEquals(tempDir.resolve("zolt.lock"), result.lockfilePath());
        assertTrue(Files.isRegularFile(tempDir.resolve("zolt.lock")));
        assertFalse(Files.exists(tempDir.resolve("apps/api/zolt.lock")));
        assertFalse(Files.exists(tempDir.resolve("modules/core/zolt.lock")));

        ZoltLockfile lockfile = lockfileReader.read(result.lockfilePath());
        assertTrue(lockfile.packages().stream().anyMatch(lockPackage ->
                lockPackage.packageId().equals(new PackageId("com.example", "app"))
                        && lockPackage.scope() == DependencyScope.COMPILE
                        && lockPackage.direct()));
        assertTrue(lockfile.packages().stream().anyMatch(lockPackage ->
                lockPackage.packageId().equals(new PackageId("com.example", "lib"))
                        && lockPackage.scope() == DependencyScope.COMPILE
                        && lockPackage.direct()));
    }

    @Test
    void usesWorkspacePlatformsForManagedMemberDependencies() throws IOException {
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

                [platforms]
                "com.example:platform" = "1.0.0"
                """.formatted(baseUri));
        member("apps/api", "api", """

                [dependencies]
                "com.example:app" = {}
                """);

        ResolveResult result = service.resolve(tempDir, tempDir.resolve("cache"), false, false);

        ZoltLockfile lockfile = lockfileReader.read(result.lockfilePath());
        assertTrue(lockfile.packages().stream().anyMatch(lockPackage ->
                lockPackage.packageId().equals(new PackageId("com.example", "app"))
                        && lockPackage.version().equals("1.0.0")
                        && lockPackage.direct()));
    }

    @Test
    void lockedWorkspaceResolveSucceedsWhenRootLockfileMatches() throws IOException {
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

        ResolveResult locked = service.resolve(tempDir, tempDir.resolve("cache"), true, false);

        assertEquals(first.resolvedCount(), locked.resolvedCount());
        assertEquals(existing, Files.readString(first.lockfilePath()));
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
    void rejectsCrossMemberVersionConflict() throws IOException {
        addArtifact("com.example", "other", "1.0.0", """
                <project>
                  <groupId>com.example</groupId>
                  <artifactId>other</artifactId>
                  <version>1.0.0</version>
                  <dependencies>
                    <dependency>
                      <groupId>com.example</groupId>
                      <artifactId>lib</artifactId>
                      <version>2.0.0</version>
                    </dependency>
                  </dependencies>
                </project>
                """);
        addArtifact("com.example", "lib", "2.0.0", pom("com.example", "lib", "2.0.0"));
        workspace("""
                [workspace]
                name = "bad"
                members = ["apps/api", "apps/worker"]

                [repositories]
                test = "%s"
                """.formatted(baseUri));
        member("apps/api", "api", """

                [dependencies]
                "com.example:app" = "1.0.0"
                """);
        member("apps/worker", "worker", """

                [dependencies]
                "com.example:other" = "1.0.0"
                """);

        ResolveException exception = assertThrows(
                ResolveException.class,
                () -> service.resolve(tempDir, tempDir.resolve("cache"), false, false));

        assertTrue(exception.getMessage().contains("Workspace dependency version conflict for com.example:lib"));
        assertTrue(exception.getMessage().contains("member `apps/api` selected 1.0.0"));
        assertTrue(exception.getMessage().contains("member `apps/worker` selected 2.0.0"));
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

    private void handle(HttpExchange exchange) throws IOException {
        byte[] body = responses.get(exchange.getRequestURI().getPath());
        if (body == null) {
            respond(exchange, 404, "missing".getBytes(StandardCharsets.UTF_8));
            return;
        }
        respond(exchange, 200, body);
    }

    private static void respond(HttpExchange exchange, int statusCode, byte[] body) throws IOException {
        try (exchange) {
            exchange.sendResponseHeaders(statusCode, body.length);
            exchange.getResponseBody().write(body);
        }
    }
}
