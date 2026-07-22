package sh.zolt.explain.maven;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import sh.zolt.maven.repository.MavenRepositoryClient;

/**
 * Serves fixture Maven POMs/BOMs from an ephemeral local {@code HttpServer} (mirroring
 * {@code MavenRepositoryClientTestSupport} in zolt-repository) so external-parent recovery can be
 * exercised end-to-end with no live network. The served base URI is handed to the resolver as its
 * "Maven Central", so a fixture never has to declare a repository.
 */
public abstract class MavenExternalParentRecoveryTestSupport {
    @TempDir
    protected Path tempDir;

    private final Map<String, byte[]> responses = new HashMap<>();
    private HttpServer server;
    protected URI baseUri;

    @BeforeEach
    protected void startServer() {
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
    protected void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    protected void putPom(String groupId, String artifactId, String version, String pom) {
        String path = groupId.replace('.', '/') + "/" + artifactId + "/" + version
                + "/" + artifactId + "-" + version + ".pom";
        responses.put("/maven2/" + path, pom.getBytes(StandardCharsets.UTF_8));
    }

    protected MavenStaticProjectInspector recoveringInspector() {
        return new MavenStaticProjectInspector(new NetworkMavenExternalParentResolver(
                new MavenRepositoryClient(), tempDir.resolve("cache"), baseUri.toString()));
    }

    protected Path writeProject(String pom) throws IOException {
        Path project = tempDir.resolve("project");
        Files.createDirectories(project);
        Files.writeString(project.resolve("pom.xml"), pom);
        return project;
    }

    private void handle(HttpExchange exchange) throws IOException {
        byte[] body = responses.get(exchange.getRequestURI().getPath());
        try (exchange) {
            if (body == null) {
                byte[] missing = "missing".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(404, missing.length);
                exchange.getResponseBody().write(missing);
                return;
            }
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
        }
    }
}
