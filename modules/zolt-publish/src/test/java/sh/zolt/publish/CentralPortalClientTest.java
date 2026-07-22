package sh.zolt.publish;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import sh.zolt.net.NetworkTransport;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class CentralPortalClientTest {
    @TempDir
    private Path tempDir;

    private HttpServer server;
    private String baseUrl;

    @BeforeEach
    void startServer() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException exception) {
            assumeTrue(false, "local HTTP server sockets are unavailable: " + exception.getMessage());
            return;
        }
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void uploadPostsMultipartBundleWithBearerTokenAndReturnsDeploymentId() throws IOException {
        Path bundle = Files.write(tempDir.resolve("central-bundle.zip"), new byte[] {0x50, 0x4b, 0x03, 0x04});
        AtomicReference<String> method = new AtomicReference<>();
        AtomicReference<String> path = new AtomicReference<>();
        AtomicReference<String> query = new AtomicReference<>();
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> contentType = new AtomicReference<>();
        AtomicReference<byte[]> body = new AtomicReference<>();
        server.createContext("/api/v1/publisher/upload", exchange -> {
            method.set(exchange.getRequestMethod());
            path.set(exchange.getRequestURI().getPath());
            query.set(exchange.getRequestURI().getQuery());
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            body.set(exchange.getRequestBody().readAllBytes());
            respond(exchange, 201, "deadbeef-0000-1111-2222-333344445555");
        });

        CentralPortalClient client = new CentralPortalClient(NetworkTransport.direct());
        String deploymentId = client.upload(
                baseUrl, bundle, "dG9rZW4=", CentralPublishingType.AUTOMATIC, Optional.of("my release"));

        assertEquals("deadbeef-0000-1111-2222-333344445555", deploymentId);
        assertEquals("POST", method.get());
        assertEquals("/api/v1/publisher/upload", path.get());
        assertTrue(query.get().contains("publishingType=AUTOMATIC"), query.get());
        assertTrue(query.get().contains("name=my+release"), query.get());
        assertEquals("Bearer dG9rZW4=", authorization.get());
        assertTrue(contentType.get().startsWith("multipart/form-data; boundary="), contentType.get());
        String rendered = new String(body.get(), StandardCharsets.UTF_8);
        assertTrue(rendered.contains("name=\"bundle\""), rendered);
        assertTrue(rendered.contains("filename=\"central-bundle.zip\""), rendered);
        assertTrue(rendered.contains("Content-Type: application/octet-stream"), rendered);
    }

    @Test
    void statusPostsIdAndParsesDeploymentState() throws IOException {
        AtomicReference<String> method = new AtomicReference<>();
        AtomicReference<String> query = new AtomicReference<>();
        AtomicReference<String> authorization = new AtomicReference<>();
        server.createContext("/api/v1/publisher/status", exchange -> {
            method.set(exchange.getRequestMethod());
            query.set(exchange.getRequestURI().getQuery());
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            respond(exchange, 200, "{\"deploymentId\":\"abc\",\"deploymentState\":\"VALIDATED\"}");
        });

        CentralPortalClient client = new CentralPortalClient(NetworkTransport.direct());
        CentralDeploymentStatus status = client.status(baseUrl + "/", "abc", "dG9rZW4=");

        assertEquals("POST", method.get());
        assertEquals("id=abc", query.get());
        assertEquals("Bearer dG9rZW4=", authorization.get());
        assertEquals("VALIDATED", status.state());
        assertEquals("abc", status.deploymentId());
    }

    @Test
    void uploadFailureRaisesActionableErrorWithoutLeakingToken() throws IOException {
        Path bundle = Files.write(tempDir.resolve("central-bundle.zip"), new byte[] {0x50, 0x4b});
        server.createContext("/api/v1/publisher/upload", exchange -> respond(exchange, 401, "unauthorized"));

        CentralPortalClient client = new CentralPortalClient(NetworkTransport.direct());
        PublishException exception = assertThrows(
                PublishException.class,
                () -> client.upload(baseUrl, bundle, "s3cr3t-token", CentralPublishingType.USER_MANAGED, Optional.empty()));

        assertTrue(exception.getMessage().contains("HTTP 401"));
        assertTrue(exception.getMessage().contains("Next:"));
        assertFalse(exception.getMessage().contains("s3cr3t-token"));
    }

    private static void respond(HttpExchange exchange, int statusCode, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        try (exchange) {
            exchange.sendResponseHeaders(statusCode, bytes.length);
            exchange.getResponseBody().write(bytes);
        }
    }
}
