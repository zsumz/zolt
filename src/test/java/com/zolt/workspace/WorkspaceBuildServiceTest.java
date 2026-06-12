package com.zolt.workspace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.zolt.build.JavacException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class WorkspaceBuildServiceTest {
    private final WorkspaceBuildService service = new WorkspaceBuildService();
    private final Map<String, byte[]> responses = new HashMap<>();

    @TempDir
    private Path tempDir;

    private HttpServer server;
    private URI baseUri;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void buildsWorkspaceMembersInDependencyOrder() throws IOException {
        workspace("""
                [workspace]
                name = "acme-platform"
                members = ["apps/api", "modules/core"]
                """);
        member("modules/core", "core", "");
        source("modules/core/src/main/java/com/acme/core/Core.java", """
                package com.acme.core;

                public final class Core {
                    private Core() {
                    }

                    public static String message() {
                        return "core";
                    }
                }
                """);
        member("apps/api", "api", """

                [dependencies]
                "com.acme:core" = { workspace = "modules/core" }
                """);
        source("apps/api/src/main/java/com/acme/api/Api.java", """
                package com.acme.api;

                import com.acme.core.Core;

                public final class Api {
                    private Api() {
                    }

                    public static String message() {
                        return Core.message();
                    }
                }
                """);

        WorkspaceBuildResult result = service.build(tempDir.resolve("apps/api"), tempDir.resolve("cache"), false);

        assertTrue(result.resolvedLockfile());
        assertEquals(List.of("modules/core", "apps/api"), result.members().stream()
                .map(WorkspaceBuildResult.MemberBuildResult::member)
                .toList());
        assertEquals(2, result.sourceCount());
        assertTrue(result.members().get(1).classpaths().compile().entries()
                .contains(tempDir.resolve("modules/core/target/classes")));
        assertTrue(Files.exists(tempDir.resolve("modules/core/target/classes/com/acme/core/Core.class")));
        assertTrue(Files.exists(tempDir.resolve("apps/api/target/classes/com/acme/api/Api.class")));
        assertTrue(Files.exists(tempDir.resolve("zolt.lock")));
    }

    @Test
    void buildsSelectedWorkspaceMembersAndDependencies() throws IOException {
        workspace("""
                [workspace]
                name = "acme-platform"
                members = ["apps/api", "modules/core", "apps/worker"]
                """);
        member("modules/core", "core", "");
        source("modules/core/src/main/java/com/acme/core/Core.java", """
                package com.acme.core;

                public final class Core {
                    private Core() {
                    }

                    public static String message() {
                        return "core";
                    }
                }
                """);
        member("apps/api", "api", """

                [dependencies]
                "com.acme:core" = { workspace = "modules/core" }
                """);
        source("apps/api/src/main/java/com/acme/api/Api.java", """
                package com.acme.api;

                import com.acme.core.Core;

                public final class Api {
                    private Api() {
                    }

                    public static String message() {
                        return Core.message();
                    }
                }
                """);
        member("apps/worker", "worker", "");
        source("apps/worker/src/main/java/com/acme/worker/Worker.java", """
                package com.acme.worker;

                public final class Worker {
                }
                """);

        WorkspaceBuildResult result = service.build(
                tempDir,
                tempDir.resolve("cache"),
                false,
                new WorkspaceSelectionRequest(false, List.of("apps/api")));

        assertEquals(List.of("modules/core", "apps/api"), result.members().stream()
                .map(WorkspaceBuildResult.MemberBuildResult::member)
                .toList());
        assertEquals(2, result.sourceCount());
        assertTrue(Files.exists(tempDir.resolve("modules/core/target/classes/com/acme/core/Core.class")));
        assertTrue(Files.exists(tempDir.resolve("apps/api/target/classes/com/acme/api/Api.class")));
        assertFalse(Files.exists(tempDir.resolve("apps/worker/target/classes/com/acme/worker/Worker.class")));
    }

    @Test
    void repeatedWorkspaceBuildSkipsAllMembersWhenInputsAreCurrent() throws IOException {
        workspace("""
                [workspace]
                name = "acme-platform"
                members = ["modules/core", "modules/util", "apps/api"]
                """);
        member("modules/core", "core", "");
        source("modules/core/src/main/java/com/acme/core/Core.java", """
                package com.acme.core;

                public final class Core {
                    private Core() {
                    }

                    public static String message() {
                        return "core";
                    }
                }
                """);
        member("modules/util", "util", "");
        source("modules/util/src/main/java/com/acme/util/Util.java", """
                package com.acme.util;

                public final class Util {
                    private Util() {
                    }

                    public static String message() {
                        return "util";
                    }
                }
                """);
        member("apps/api", "api", """

                [dependencies]
                "com.acme:core" = { workspace = "modules/core" }
                "com.acme:util" = { workspace = "modules/util" }
                """);
        source("apps/api/src/main/java/com/acme/api/Api.java", """
                package com.acme.api;

                import com.acme.core.Core;
                import com.acme.util.Util;

                public final class Api {
                    private Api() {
                    }

                    public static String message() {
                        return Core.message() + Util.message();
                    }
                }
                """);

        WorkspaceBuildResult first = service.build(tempDir.resolve("apps/api"), tempDir.resolve("cache"), false);
        WorkspaceBuildResult second = service.build(tempDir.resolve("apps/api"), tempDir.resolve("cache"), false);

        assertEquals(3, first.mainCompilationExecutedCount());
        assertEquals(0, first.mainCompilationSkippedCount());
        assertEquals(0, second.mainCompilationExecutedCount());
        assertEquals(3, second.mainCompilationSkippedCount());
    }

    @Test
    void upstreamImplementationOnlyChangeDoesNotInvalidateDependentWorkspaceCompilation() throws IOException {
        workspace("""
                [workspace]
                name = "acme-platform"
                members = ["modules/core", "modules/util", "apps/api"]
                """);
        member("modules/core", "core", "");
        Path coreSource = tempDir.resolve("modules/core/src/main/java/com/acme/core/Core.java");
        source("modules/core/src/main/java/com/acme/core/Core.java", """
                package com.acme.core;

                public final class Core {
                    private Core() {
                    }

                    public static String message() {
                        return "core";
                    }
                }
                """);
        member("modules/util", "util", "");
        source("modules/util/src/main/java/com/acme/util/Util.java", """
                package com.acme.util;

                public final class Util {
                    private Util() {
                    }

                    public static String message() {
                        return "util";
                    }
                }
                """);
        member("apps/api", "api", """

                [dependencies]
                "com.acme:core" = { workspace = "modules/core" }
                "com.acme:util" = { workspace = "modules/util" }
                """);
        source("apps/api/src/main/java/com/acme/api/Api.java", """
                package com.acme.api;

                import com.acme.core.Core;
                import com.acme.util.Util;

                public final class Api {
                    private Api() {
                    }

                    public static String message() {
                        return Core.message() + Util.message();
                    }
                }
                """);
        service.build(tempDir.resolve("apps/api"), tempDir.resolve("cache"), false);
        service.build(tempDir.resolve("apps/api"), tempDir.resolve("cache"), false);
        Files.writeString(coreSource, """
                package com.acme.core;

                public final class Core {
                    private Core() {
                    }

                    public static String message() {
                        return "core changed";
                    }
                }
                """);

        WorkspaceBuildResult result = service.build(tempDir.resolve("apps/api"), tempDir.resolve("cache"), false);
        Map<String, Boolean> skippedByMember = new HashMap<>();
        for (WorkspaceBuildResult.MemberBuildResult member : result.members()) {
            skippedByMember.put(member.member(), member.result().mainCompilationSkipped());
        }

        assertFalse(skippedByMember.get("modules/core"));
        assertTrue(skippedByMember.get("modules/util"));
        assertTrue(skippedByMember.get("apps/api"));
    }

    @Test
    void upstreamAbiChangeInvalidatesDependentWorkspaceCompilation() throws IOException {
        workspace("""
                [workspace]
                name = "acme-platform"
                members = ["modules/core", "modules/util", "apps/api"]
                """);
        member("modules/core", "core", "");
        Path coreSource = tempDir.resolve("modules/core/src/main/java/com/acme/core/Core.java");
        source("modules/core/src/main/java/com/acme/core/Core.java", """
                package com.acme.core;

                public final class Core {
                    private Core() {
                    }

                    public static String message() {
                        return "core";
                    }
                }
                """);
        member("modules/util", "util", "");
        source("modules/util/src/main/java/com/acme/util/Util.java", """
                package com.acme.util;

                public final class Util {
                    private Util() {
                    }

                    public static String message() {
                        return "util";
                    }
                }
                """);
        member("apps/api", "api", """

                [dependencies]
                "com.acme:core" = { workspace = "modules/core" }
                "com.acme:util" = { workspace = "modules/util" }
                """);
        source("apps/api/src/main/java/com/acme/api/Api.java", """
                package com.acme.api;

                import com.acme.core.Core;
                import com.acme.util.Util;

                public final class Api {
                    private Api() {
                    }

                    public static String message() {
                        return Core.message() + Util.message();
                    }
                }
                """);
        service.build(tempDir.resolve("apps/api"), tempDir.resolve("cache"), false);
        service.build(tempDir.resolve("apps/api"), tempDir.resolve("cache"), false);
        Files.writeString(coreSource, """
                package com.acme.core;

                public final class Core {
                    private Core() {
                    }

                    public static String message() {
                        return "core changed";
                    }

                    public static String extra() {
                        return "extra";
                    }
                }
                """);

        WorkspaceBuildResult result = service.build(tempDir.resolve("apps/api"), tempDir.resolve("cache"), false);
        Map<String, Boolean> skippedByMember = new HashMap<>();
        for (WorkspaceBuildResult.MemberBuildResult member : result.members()) {
            skippedByMember.put(member.member(), member.result().mainCompilationSkipped());
        }

        assertFalse(skippedByMember.get("modules/core"));
        assertTrue(skippedByMember.get("modules/util"));
        assertFalse(skippedByMember.get("apps/api"));
    }

    @Test
    void failsWhenMemberImportsWorkspaceProjectWithoutDeclaringDependency() throws IOException {
        workspace("""
                [workspace]
                name = "acme-platform"
                members = ["modules/core", "modules/extra", "apps/api", "apps/worker"]
                """);
        member("modules/core", "core", "");
        source("modules/core/src/main/java/com/acme/core/Core.java", """
                package com.acme.core;

                public final class Core {
                    private Core() {
                    }

                    public static String message() {
                        return "core";
                    }
                }
                """);
        member("modules/extra", "extra", "");
        source("modules/extra/src/main/java/com/acme/extra/Extra.java", """
                package com.acme.extra;

                public final class Extra {
                    private Extra() {
                    }

                    public static String message() {
                        return "extra";
                    }
                }
                """);
        member("apps/api", "api", """

                [dependencies]
                "com.acme:core" = { workspace = "modules/core" }
                """);
        source("apps/api/src/main/java/com/acme/api/Api.java", """
                package com.acme.api;

                import com.acme.core.Core;
                import com.acme.extra.Extra;

                public final class Api {
                    private Api() {
                    }

                    public static String message() {
                        return Core.message() + Extra.message();
                    }
                }
                """);
        member("apps/worker", "worker", """

                [dependencies]
                "com.acme:extra" = { workspace = "modules/extra" }
                """);
        source("apps/worker/src/main/java/com/acme/worker/Worker.java", """
                package com.acme.worker;

                import com.acme.extra.Extra;

                public final class Worker {
                    private Worker() {
                    }

                    public static String message() {
                        return Extra.message();
                    }
                }
                """);

        JavacException exception = assertThrows(
                JavacException.class,
                () -> service.build(
                        tempDir,
                        tempDir.resolve("cache"),
                        false,
                        new WorkspaceSelectionRequest(true, List.of())));

        assertTrue(exception.getMessage().contains("javac failed with exit code"));
        assertTrue(exception.getMessage().contains("Api.java"));
        assertTrue(exception.getMessage().contains("com.acme.extra"));
    }

    @Test
    void downstreamMemberCompilesAgainstExportedApiDependency() throws IOException {
        startRepository();
        addJarArtifact(
                "com.example",
                "contract",
                "1.0.0",
                "com.example.contract.Contract",
                """
                package com.example.contract;

                public final class Contract {
                    private final String value;

                    public Contract(String value) {
                        this.value = value;
                    }

                    public String value() {
                        return value;
                    }
                }
                """);
        addJarArtifact(
                "com.example",
                "internal",
                "1.0.0",
                "com.example.internal.Internal",
                """
                package com.example.internal;

                public final class Internal {
                    private Internal() {
                    }

                    public static String value() {
                        return "internal";
                    }
                }
                """);
        workspace("""
                [workspace]
                name = "acme-platform"
                members = ["modules/core", "apps/api"]

                [repositories]
                test = "%s"
                """.formatted(baseUri));
        member("modules/core", "core", """

                [api.dependencies]
                "com.example:contract" = "1.0.0"

                [dependencies]
                "com.example:internal" = "1.0.0"
                """);
        source("modules/core/src/main/java/com/acme/core/Core.java", """
                package com.acme.core;

                import com.example.contract.Contract;
                import com.example.internal.Internal;

                public final class Core {
                    private Core() {
                    }

                    public static Contract contract() {
                        return new Contract(Internal.value());
                    }
                }
                """);
        member("apps/api", "api", """

                [dependencies]
                "com.acme:core" = { workspace = "modules/core" }
                """);
        source("apps/api/src/main/java/com/acme/api/Api.java", """
                package com.acme.api;

                import com.acme.core.Core;
                import com.example.contract.Contract;

                public final class Api {
                    private Api() {
                    }

                    public static String message() {
                        Contract contract = Core.contract();
                        return contract.value();
                    }
                }
                """);

        WorkspaceBuildResult result = service.build(tempDir.resolve("apps/api"), tempDir.resolve("cache"), false);

        assertEquals(2, result.sourceCount());
        assertTrue(Files.exists(tempDir.resolve("apps/api/target/classes/com/acme/api/Api.class")));
    }

    @Test
    void downstreamMemberCannotCompileAgainstPrivateDependencyOfWorkspaceDependency() throws IOException {
        startRepository();
        addJarArtifact(
                "com.example",
                "contract",
                "1.0.0",
                "com.example.contract.Contract",
                """
                package com.example.contract;

                public final class Contract {
                }
                """);
        addJarArtifact(
                "com.example",
                "internal",
                "1.0.0",
                "com.example.internal.Internal",
                """
                package com.example.internal;

                public final class Internal {
                    private Internal() {
                    }

                    public static String value() {
                        return "internal";
                    }
                }
                """);
        workspace("""
                [workspace]
                name = "acme-platform"
                members = ["modules/core", "apps/api"]

                [repositories]
                test = "%s"
                """.formatted(baseUri));
        member("modules/core", "core", """

                [api.dependencies]
                "com.example:contract" = "1.0.0"

                [dependencies]
                "com.example:internal" = "1.0.0"
                """);
        source("modules/core/src/main/java/com/acme/core/Core.java", """
                package com.acme.core;

                import com.example.contract.Contract;
                import com.example.internal.Internal;

                public final class Core {
                    private Core() {
                    }

                    public static Contract contract() {
                        Internal.value();
                        return new Contract();
                    }
                }
                """);
        member("apps/api", "api", """

                [dependencies]
                "com.acme:core" = { workspace = "modules/core" }
                """);
        source("apps/api/src/main/java/com/acme/api/Api.java", """
                package com.acme.api;

                import com.acme.core.Core;
                import com.example.internal.Internal;

                public final class Api {
                    private Api() {
                    }

                    public static String message() {
                        Core.contract();
                        return Internal.value();
                    }
                }
                """);

        JavacException exception = assertThrows(
                JavacException.class,
                () -> service.build(tempDir.resolve("apps/api"), tempDir.resolve("cache"), false));

        assertTrue(exception.getMessage().contains("javac failed with exit code"));
        assertTrue(exception.getMessage().contains("Api.java"));
        assertTrue(exception.getMessage().contains("com.example.internal"));
        assertTrue(exception.getMessage().contains("move it to [api.dependencies]"));
        assertTrue(Files.exists(tempDir.resolve("modules/core/target/classes/com/acme/core/Core.class")));
        assertFalse(Files.exists(tempDir.resolve("apps/api/target/classes/com/acme/api/Api.class")));
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
                java = "%s"
                %s""".formatted(name, currentJavaMajorVersion(), extraToml));
    }

    private void source(String path, String content) throws IOException {
        Path source = tempDir.resolve(path);
        Files.createDirectories(source.getParent());
        Files.writeString(source, content);
    }

    private void startRepository() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.start();
        baseUri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/maven2/");
    }

    private void addJarArtifact(
            String groupId,
            String artifactId,
            String version,
            String className,
            String source) throws IOException {
        String base = "/maven2/" + groupId.replace('.', '/') + "/" + artifactId + "/" + version + "/" + artifactId + "-" + version;
        responses.put(base + ".pom", pom(groupId, artifactId, version).getBytes(StandardCharsets.UTF_8));
        responses.put(base + ".jar", jarBytes(groupId, artifactId, version, className, source));
    }

    private byte[] jarBytes(
            String groupId,
            String artifactId,
            String version,
            String className,
            String source) throws IOException {
        Path fixtureRoot = tempDir.resolve("fixture-jars").resolve(groupId + "." + artifactId + "." + version);
        Path sourceFile = fixtureRoot.resolve("src").resolve(className.replace('.', '/') + ".java");
        Path classes = fixtureRoot.resolve("classes");
        Files.createDirectories(sourceFile.getParent());
        Files.createDirectories(classes);
        Files.writeString(sourceFile, source);
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("JDK compiler is required for workspace API dependency fixture tests.");
        }
        int exitCode = compiler.run(null, null, null, "-d", classes.toString(), sourceFile.toString());
        if (exitCode != 0) {
            throw new IllegalStateException("Fixture Java source failed to compile: " + className);
        }
        Path jar = fixtureRoot.resolve(artifactId + "-" + version + ".jar");
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar))) {
            List<Path> classFiles;
            try (var stream = Files.walk(classes)) {
                classFiles = stream
                        .filter(Files::isRegularFile)
                        .sorted()
                        .toList();
            }
            for (Path classFile : classFiles) {
                String entryName = classes.relativize(classFile).toString().replace('\\', '/');
                output.putNextEntry(new JarEntry(entryName));
                Files.copy(classFile, output);
                output.closeEntry();
            }
        }
        return Files.readAllBytes(jar);
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

    private static String currentJavaMajorVersion() {
        String version = System.getProperty("java.version");
        String[] parts = version.split("[._+-]", -1);
        if (parts.length >= 2 && "1".equals(parts[0])) {
            return parts[1];
        }
        return parts[0];
    }
}
