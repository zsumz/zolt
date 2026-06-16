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

final class WorkspaceBuildServiceDependencyVisibilityTest {
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
        String base = "/maven2/%s/%s/%s/%s-%s"
                .formatted(groupId.replace('.', '/'), artifactId, version, artifactId, version);
        responses.put(base + ".pom", pom(groupId, artifactId, version).getBytes(StandardCharsets.UTF_8));
        responses.put(base + ".jar", jarBytes(groupId, artifactId, version, className, source));
    }

    private byte[] jarBytes(String groupId, String artifactId, String version, String className, String source)
            throws IOException {
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
        if (compiler.run(null, null, null, "-d", classes.toString(), sourceFile.toString()) != 0) {
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
