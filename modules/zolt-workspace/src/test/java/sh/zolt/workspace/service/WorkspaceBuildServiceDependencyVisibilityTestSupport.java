package sh.zolt.workspace.service;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import sh.zolt.build.JavacException;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

abstract class WorkspaceBuildServiceDependencyVisibilityTestSupport {
    final WorkspaceBuildService service = new WorkspaceBuildService();
    final Map<String, byte[]> responses = new HashMap<>();

    @TempDir
    Path tempDir;

    HttpServer server;
    URI baseUri;

    @BeforeEach
    void startServer() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException exception) {
            assumeTrue(false, "local HTTP server sockets are unavailable: " + exception.getMessage());
            return;
        }
        server.createContext("/", this::handle);
        server.start();
        baseUri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/maven2/");
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    final void workspace(String content) throws IOException {
        Files.writeString(tempDir.resolve("zolt-workspace.toml"), content);
    }

    final void member(String path, String name, String extraToml) throws IOException {
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

    final void source(String path, String content) throws IOException {
        Path source = tempDir.resolve(path);
        Files.createDirectories(source.getParent());
        Files.writeString(source, content);
    }

    final void addJarArtifact(
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
