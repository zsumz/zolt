package com.zolt.workspace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.zolt.lockfile.LockPackage;
import com.zolt.lockfile.ZoltLockfile;
import com.zolt.lockfile.ZoltLockfileReader;
import com.zolt.dependency.DependencyScope;
import com.zolt.dependency.PackageId;
import com.zolt.classpath.LockfileClasspathPackageConverter;
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

        assertEquals(3, result.resolvedCount());
        assertEquals(4, result.downloadCount());
        assertEquals(0, result.conflictCount());
        assertEquals(tempDir.resolve("zolt.lock"), result.lockfilePath());
        assertTrue(Files.isRegularFile(tempDir.resolve("zolt.lock")));
        assertFalse(Files.exists(tempDir.resolve("apps/api/zolt.lock")));
        assertFalse(Files.exists(tempDir.resolve("modules/core/zolt.lock")));

        ZoltLockfile lockfile = lockfileReader.read(result.lockfilePath());
        assertTrue(lockfile.projectResolutionFingerprint().orElseThrow().startsWith("sha256:"));
        assertTrue(lockfile.packages().stream().anyMatch(lockPackage ->
                lockPackage.packageId().equals(new PackageId("com.acme", "core"))
                        && lockPackage.version().equals("0.1.0")
                        && lockPackage.source().equals("workspace")
                        && lockPackage.scope() == DependencyScope.COMPILE
                        && lockPackage.direct()
                        && lockPackage.workspace().orElseThrow().equals("modules/core")
                        && lockPackage.workspaceOutput().orElseThrow().equals("target/classes")
                        && lockPackage.members().equals(List.of("apps/api"))));
        assertTrue(lockfile.packages().stream().anyMatch(lockPackage ->
                lockPackage.packageId().equals(new PackageId("com.example", "app"))
                        && lockPackage.scope() == DependencyScope.COMPILE
                        && lockPackage.direct()
                        && lockPackage.members().equals(List.of("apps/api"))));
        assertTrue(lockfile.packages().stream().anyMatch(lockPackage ->
                lockPackage.packageId().equals(new PackageId("com.example", "lib"))
                        && lockPackage.scope() == DependencyScope.COMPILE
                        && lockPackage.direct()
                        && lockPackage.members().equals(List.of("apps/api", "modules/core"))));

        assertTrue(LockfileClasspathPackageConverter.classpathPackages(lockfile, tempDir.resolve("cache"), tempDir).stream()
                .anyMatch(classpathPackage -> classpathPackage.resolvedPackage().jarPath()
                        .equals(tempDir.resolve("modules/core/target/classes"))));
    }

    @Test
    void mergesWorkspacePackageOwners() throws IOException {
        workspace("""
                [workspace]
                name = "acme-platform"
                members = ["apps/api", "apps/worker", "modules/core"]
                """);
        member("apps/api", "api", """

                [dependencies]
                "com.acme:core" = { workspace = "modules/core" }
                """);
        member("apps/worker", "worker", """

                [dependencies]
                "com.acme:core" = { workspace = "modules/core" }
                """);
        member("modules/core", "core", "");

        ResolveResult result = service.resolve(tempDir, tempDir.resolve("cache"), false, false);

        ZoltLockfile lockfile = lockfileReader.read(result.lockfilePath());
        LockPackage core = lockfile.packages().stream()
                .filter(lockPackage -> lockPackage.packageId().equals(new PackageId("com.acme", "core")))
                .findFirst()
                .orElseThrow();
        assertEquals("workspace", core.source());
        assertEquals("modules/core", core.workspace().orElseThrow());
        assertEquals("target/classes", core.workspaceOutput().orElseThrow());
        assertEquals(List.of("apps/api", "apps/worker"), core.members());
    }

    @Test
    void rejectsUnsafeWorkspaceMemberOutputBeforeWritingLockfile() throws IOException {
        workspace("""
                [workspace]
                name = "bad"
                members = ["apps/api", "modules/core"]
                """);
        member("apps/api", "api", """

                [dependencies]
                "com.acme:core" = { workspace = "modules/core" }
                """);
        member("modules/core", "core", """

                [build]
                output = "../classes"
                """);

        ResolveException exception = assertThrows(
                ResolveException.class,
                () -> service.resolve(tempDir, tempDir.resolve("cache"), false, false));

        assertTrue(exception.getMessage().contains("Workspace member `modules/core` has an invalid [build].output"));
        assertTrue(exception.getMessage().contains("[build].output"));
        assertTrue(exception.getMessage().contains("../classes"));
        assertFalse(Files.exists(tempDir.resolve("zolt.lock")));
    }

    @Test
    void recordsExportedWorkspacePackageOwners() throws IOException {
        workspace("""
                [workspace]
                name = "acme-platform"
                members = ["apps/api", "modules/core"]
                """);
        member("apps/api", "api", """

                [api.dependencies]
                "com.acme:core" = { workspace = "modules/core" }
                """);
        member("modules/core", "core", "");

        ResolveResult result = service.resolve(tempDir, tempDir.resolve("cache"), false, false);

        ZoltLockfile lockfile = lockfileReader.read(result.lockfilePath());
        LockPackage core = lockfile.packages().stream()
                .filter(lockPackage -> lockPackage.packageId().equals(new PackageId("com.acme", "core")))
                .findFirst()
                .orElseThrow();
        assertEquals(List.of("apps/api"), core.members());
        assertEquals(List.of("apps/api"), core.exportedBy());
    }

    @Test
    void recordsExportedExternalApiDependencies() throws IOException {
        workspace("""
                [workspace]
                name = "acme-platform"
                members = ["apps/api"]

                [repositories]
                test = "%s"
                """.formatted(baseUri));
        member("apps/api", "api", """

                [api.dependencies]
                "com.example:app" = "1.0.0"
                """);

        ResolveResult result = service.resolve(tempDir, tempDir.resolve("cache"), false, false);

        ZoltLockfile lockfile = lockfileReader.read(result.lockfilePath());
        LockPackage app = lockfile.packages().stream()
                .filter(lockPackage -> lockPackage.packageId().equals(new PackageId("com.example", "app")))
                .findFirst()
                .orElseThrow();
        LockPackage lib = lockfile.packages().stream()
                .filter(lockPackage -> lockPackage.packageId().equals(new PackageId("com.example", "lib")))
                .findFirst()
                .orElseThrow();
        assertEquals(List.of("apps/api"), app.members());
        assertEquals(List.of("apps/api"), app.exportedBy());
        assertEquals(List.of("apps/api"), lib.members());
        assertEquals(List.of(), lib.exportedBy());
    }

    @Test
    void preservesDependencyMetadataWhenMergingWorkspacePolicy() throws IOException {
        workspace("""
                [workspace]
                name = "acme-platform"
                members = ["apps/api"]

                [repositories]
                test = "%s"
                """.formatted(baseUri));
        member("apps/api", "api", """

                [dependencies]
                "com.example:app" = { version = "1.0.0", exclusions = [{ group = "com.example", artifact = "lib" }] }
                """);

        ResolveResult result = service.resolve(tempDir, tempDir.resolve("cache"), false, false);

        assertEquals(1, result.resolvedCount());
        ZoltLockfile lockfile = lockfileReader.read(result.lockfilePath());
        assertTrue(lockfile.packages().stream().anyMatch(lockPackage ->
                lockPackage.packageId().equals(new PackageId("com.example", "app"))));
        assertFalse(lockfile.packages().stream().anyMatch(lockPackage ->
                lockPackage.packageId().equals(new PackageId("com.example", "lib"))));
    }

    @Test
    void recordsExportedManagedApiDependencies() throws IOException {
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

                [api.dependencies]
                "com.example:app" = {}
                """);

        ResolveResult result = service.resolve(tempDir, tempDir.resolve("cache"), false, false);

        ZoltLockfile lockfile = lockfileReader.read(result.lockfilePath());
        LockPackage app = lockfile.packages().stream()
                .filter(lockPackage -> lockPackage.packageId().equals(new PackageId("com.example", "app")))
                .findFirst()
                .orElseThrow();
        assertEquals("1.0.0", app.version());
        assertEquals(List.of("apps/api"), app.exportedBy());
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

    @Test
    void selectsGlobalExternalVersionsAcrossWorkspaceMembers() throws IOException {
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

        ResolveResult result = service.resolve(tempDir, tempDir.resolve("cache"), false, false);

        assertEquals(3, result.resolvedCount());
        assertEquals(1, result.conflictCount());

        ZoltLockfile lockfile = lockfileReader.read(result.lockfilePath());
        assertFalse(lockfile.packages().stream().anyMatch(lockPackage ->
                lockPackage.packageId().equals(new PackageId("com.example", "lib"))
                        && lockPackage.version().equals("1.0.0")));
        LockPackage lib = lockfile.packages().stream()
                .filter(lockPackage -> lockPackage.packageId().equals(new PackageId("com.example", "lib")))
                .findFirst()
                .orElseThrow();
        assertEquals("2.0.0", lib.version());
        assertEquals(List.of("apps/api", "apps/worker"), lib.members());

        LockPackage app = lockfile.packages().stream()
                .filter(lockPackage -> lockPackage.packageId().equals(new PackageId("com.example", "app")))
                .findFirst()
                .orElseThrow();
        assertEquals(List.of("com.example:lib:2.0.0"), app.dependencies());
        assertTrue(lockfile.conflicts().stream().anyMatch(conflict ->
                conflict.packageId().equals(new PackageId("com.example", "lib"))
                        && conflict.selectedVersion().equals("2.0.0")
                        && conflict.requestedVersions().equals(List.of("1.0.0", "2.0.0"))));
    }

    @Test
    void directWorkspaceMemberDependencyWinsOverTransitiveWorkspaceRequest() throws IOException {
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
                name = "direct-wins"
                members = ["apps/api", "apps/worker"]

                [repositories]
                test = "%s"
                """.formatted(baseUri));
        member("apps/api", "api", """

                [dependencies]
                "com.example:lib" = "1.0.0"
                """);
        member("apps/worker", "worker", """

                [dependencies]
                "com.example:other" = "1.0.0"
                """);

        ResolveResult result = service.resolve(tempDir, tempDir.resolve("cache"), false, false);

        assertEquals(2, result.resolvedCount());
        assertEquals(1, result.conflictCount());

        ZoltLockfile lockfile = lockfileReader.read(result.lockfilePath());
        LockPackage lib = lockfile.packages().stream()
                .filter(lockPackage -> lockPackage.packageId().equals(new PackageId("com.example", "lib")))
                .findFirst()
                .orElseThrow();
        assertEquals("1.0.0", lib.version());
        assertEquals(List.of("apps/api", "apps/worker"), lib.members());
        assertTrue(lib.direct());

        LockPackage other = lockfile.packages().stream()
                .filter(lockPackage -> lockPackage.packageId().equals(new PackageId("com.example", "other")))
                .findFirst()
                .orElseThrow();
        assertEquals(List.of("com.example:lib:1.0.0"), other.dependencies());
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
