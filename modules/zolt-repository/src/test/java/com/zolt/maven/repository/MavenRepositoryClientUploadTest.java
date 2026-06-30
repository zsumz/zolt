package com.zolt.maven.repository;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.zolt.maven.ArtifactDescriptor;
import com.zolt.maven.Coordinate;
import com.zolt.maven.CoordinateParser;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class MavenRepositoryClientUploadTest {
    private final CoordinateParser parser = new CoordinateParser();
    private final MavenRepositoryClient client = new MavenRepositoryClient();

    private HttpServer server;
    private URI baseUri;

    @TempDir
    private Path tempDir;

    @BeforeEach
    void startServer() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException exception) {
            assumeTrue(false, "local HTTP server sockets are unavailable: " + exception.getMessage());
            return;
        }
        server.start();
        baseUri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/maven2/");
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void uploadsArtifactWithPutBodyAndBasicAuthentication() throws IOException {
        Coordinate coordinate = parser.parse("com.google.guava:guava:33.4.0-jre");
        Path source = tempDir.resolve("guava-33.4.0-jre.jar");
        byte[] bytes = new byte[] {0x50, 0x4b, 0x03, 0x04};
        Files.write(source, bytes);
        AtomicReference<String> method = new AtomicReference<>();
        AtomicReference<String> path = new AtomicReference<>();
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<byte[]> body = new AtomicReference<>();
        server.createContext("/publish/", exchange -> {
            method.set(exchange.getRequestMethod());
            path.set(exchange.getRequestURI().getPath());
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            body.set(exchange.getRequestBody().readAllBytes());
            respond(exchange, 201, "created".getBytes(StandardCharsets.UTF_8));
        });
        URI publishBaseUri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/publish/");

        client.uploadArtifact(
                publishBaseUri,
                ArtifactDescriptor.jar(coordinate),
                source,
                Optional.of(new RepositoryAuthentication("zolt-user", "zolt-secret")));

        assertEquals("PUT", method.get());
        assertEquals(
                "/publish/com/google/guava/guava/33.4.0-jre/guava-33.4.0-jre.jar",
                path.get());
        assertEquals(
                "Basic " + Base64.getEncoder().encodeToString("zolt-user:zolt-secret".getBytes(StandardCharsets.UTF_8)),
                authorization.get());
        assertArrayEquals(bytes, body.get());
    }

    @Test
    void uploadsPomToRepositoryPath() throws IOException {
        Coordinate coordinate = parser.parse("com.google.guava:guava:33.4.0-jre");
        Path source = tempDir.resolve("guava-33.4.0-jre.pom");
        Files.writeString(source, "<project/>");
        AtomicReference<String> path = new AtomicReference<>();
        AtomicReference<byte[]> body = new AtomicReference<>();
        server.createContext("/publish-pom/", exchange -> {
            path.set(exchange.getRequestURI().getPath());
            body.set(exchange.getRequestBody().readAllBytes());
            respond(exchange, 200, "ok".getBytes(StandardCharsets.UTF_8));
        });
        URI publishBaseUri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/publish-pom/");

        client.uploadPom(publishBaseUri, coordinate, source);

        assertEquals(
                "/publish-pom/com/google/guava/guava/33.4.0-jre/guava-33.4.0-jre.pom",
                path.get());
        assertArrayEquals("<project/>".getBytes(StandardCharsets.UTF_8), body.get());
    }

    @Test
    void transientUploadStatusIsRetriedUntilSuccess() throws IOException {
        Coordinate coordinate = parser.parse("com.google.guava:guava:33.4.0-jre");
        Path source = tempDir.resolve("guava-33.4.0-jre.pom");
        Files.writeString(source, "<project/>");
        AtomicInteger attempts = new AtomicInteger();
        server.createContext("/flaky-upload/", exchange -> {
            int attempt = attempts.incrementAndGet();
            if (attempt == 1) {
                respond(exchange, 503, "slow down".getBytes(StandardCharsets.UTF_8));
                return;
            }
            respond(exchange, 201, "created".getBytes(StandardCharsets.UTF_8));
        });
        URI publishBaseUri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/flaky-upload/");
        MavenRepositoryClient retryingClient = retryingClient(3);

        retryingClient.uploadPom(publishBaseUri, coordinate, source);

        assertEquals(2, attempts.get());
    }

    @Test
    void uploadFailureMessageIsActionableAndDoesNotLeakCredentials() throws IOException {
        Coordinate coordinate = parser.parse("com.google.guava:guava:33.4.0-jre");
        Path source = tempDir.resolve("guava-33.4.0-jre.jar");
        Files.writeString(source, "jar");
        server.createContext("/forbidden-upload/", exchange ->
                respond(exchange, 403, "forbidden".getBytes(StandardCharsets.UTF_8)));
        URI publishBaseUri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/forbidden-upload/");

        RepositoryClientException exception = assertThrows(
                RepositoryClientException.class,
                () -> client.uploadArtifact(
                        publishBaseUri,
                        ArtifactDescriptor.jar(coordinate),
                        source,
                        Optional.of(new RepositoryAuthentication("zolt-user", "zolt-secret"))));

        assertTrue(exception.getMessage().contains("Repository returned HTTP 403 while uploading"));
        assertTrue(exception.getMessage().contains("Try again or check the repository URL."));
        assertFalse(exception.getMessage().contains("zolt-user"));
        assertFalse(exception.getMessage().contains("zolt-secret"));
    }

    @Test
    void missingUploadSourceMessageIsActionable() {
        Coordinate coordinate = parser.parse("com.google.guava:guava:33.4.0-jre");
        Path missing = tempDir.resolve("missing.jar");

        RepositoryClientException exception = assertThrows(
                RepositoryClientException.class,
                () -> client.uploadArtifact(baseUri, ArtifactDescriptor.jar(coordinate), missing));

        assertTrue(exception.getMessage().contains("Could not read upload source for com.google.guava:guava:33.4.0-jre"));
        assertTrue(exception.getMessage().contains("Check that the file exists and is readable."));
    }

    private static MavenRepositoryClient retryingClient(int maxAttempts) {
        return new MavenRepositoryClient(
                HttpClient.newHttpClient(),
                new MavenRepositoryPathBuilder(),
                new RepositoryHttpPolicy(Duration.ofSeconds(5), maxAttempts, Duration.ZERO));
    }

    private static void respond(HttpExchange exchange, int statusCode, byte[] body) throws IOException {
        try (exchange) {
            exchange.sendResponseHeaders(statusCode, body.length);
            exchange.getResponseBody().write(body);
        }
    }
}
