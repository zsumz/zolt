package com.zolt.maven;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class MavenRepositoryClientTest {
    private final CoordinateParser parser = new CoordinateParser();
    private final MavenRepositoryClient client = new MavenRepositoryClient();
    private final Map<String, byte[]> responses = new HashMap<>();
    private final Map<String, AtomicInteger> requestCounts = new ConcurrentHashMap<>();

    private HttpServer server;
    private URI baseUri;

    @TempDir
    private Path tempDir;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.start();
        baseUri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/maven2/");
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void fetchesKnownPom() {
        Coordinate coordinate = parser.parse("com.google.guava:guava:33.4.0-jre");
        put("com/google/guava/guava/33.4.0-jre/guava-33.4.0-jre.pom", "<project/>");

        RepositoryArtifact artifact = client.fetchPom(baseUri, coordinate);

        assertEquals(coordinate, artifact.coordinate());
        assertEquals("com/google/guava/guava/33.4.0-jre/guava-33.4.0-jre.pom", artifact.path());
        assertEquals(
                baseUri.resolve("com/google/guava/guava/33.4.0-jre/guava-33.4.0-jre.pom"),
                artifact.source());
        assertArrayEquals("<project/>".getBytes(StandardCharsets.UTF_8), artifact.bytes());
    }

    @Test
    void fetchesKnownJar() {
        Coordinate coordinate = parser.parse("com.google.guava:guava:33.4.0-jre");
        byte[] jarBytes = new byte[] {0x50, 0x4b, 0x03, 0x04};
        responses.put("/maven2/com/google/guava/guava/33.4.0-jre/guava-33.4.0-jre.jar", jarBytes);

        RepositoryArtifact artifact = client.fetchJar(baseUri, coordinate);

        assertArrayEquals(jarBytes, artifact.bytes());
    }

    @Test
    void fetchesClassifierArtifact() {
        Coordinate coordinate = parser.parse("io.quarkus:quarkus-custom-deployment:1.0.0");
        byte[] jarBytes = new byte[] {0x50, 0x4b, 0x03, 0x04};
        responses.put(
                "/maven2/io/quarkus/quarkus-custom-deployment/1.0.0/quarkus-custom-deployment-1.0.0-deployment.jar",
                jarBytes);

        RepositoryArtifact artifact = client.fetchArtifact(
                baseUri,
                ArtifactDescriptor.jar(coordinate, Optional.of("deployment")));

        assertEquals(
                "io/quarkus/quarkus-custom-deployment/1.0.0/quarkus-custom-deployment-1.0.0-deployment.jar",
                artifact.path());
        assertArrayEquals(jarBytes, artifact.bytes());
    }

    @Test
    void missingArtifactMessageIsActionable() {
        Coordinate coordinate = parser.parse("com.google.guava:missing:1.0.0");

        RepositoryMissingArtifactException exception = assertThrows(
                RepositoryMissingArtifactException.class,
                () -> client.fetchPom(baseUri, coordinate));

        assertTrue(exception.getMessage().contains("Could not find com.google.guava:missing:1.0.0"));
        assertTrue(exception.getMessage().contains("Check the group, artifact, version, and repository URL."));
        assertEquals(
                1,
                requestCount("/maven2/com/google/guava/missing/1.0.0/missing-1.0.0.pom"));
    }

    @Test
    void networkFailureMessageIsActionable() {
        Coordinate coordinate = parser.parse("com.google.guava:guava:33.4.0-jre");
        URI unreachable = URI.create("http://127.0.0.1:1/maven2/");

        RepositoryClientException exception = assertThrows(
                RepositoryClientException.class,
                () -> client.fetchPom(unreachable, coordinate));

        assertTrue(exception.getMessage().contains("Could not download com.google.guava:guava:33.4.0-jre"));
        assertTrue(exception.getMessage().contains("Check your network, proxy, or repository URL and try again."));
    }

    @Test
    void nonSuccessStatusMessageIsActionable() {
        Coordinate coordinate = parser.parse("com.google.guava:guava:33.4.0-jre");
        server.createContext("/error/", exchange -> respond(exchange, 500, "nope".getBytes(StandardCharsets.UTF_8)));
        URI errorBaseUri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/error/");

        RepositoryClientException exception = assertThrows(
                RepositoryClientException.class,
                () -> client.fetchPom(errorBaseUri, coordinate));

        assertTrue(exception.getMessage().contains("Repository returned HTTP 500"));
        assertTrue(exception.getMessage().contains("Try again or check the repository URL."));
    }

    @Test
    void transientStatusIsRetriedUntilSuccess() {
        Coordinate coordinate = parser.parse("com.google.guava:guava:33.4.0-jre");
        AtomicInteger attempts = new AtomicInteger();
        server.createContext("/flaky/", exchange -> {
            int attempt = attempts.incrementAndGet();
            if (attempt == 1) {
                respond(exchange, 503, "slow down".getBytes(StandardCharsets.UTF_8));
                return;
            }
            respond(exchange, 200, "<project/>".getBytes(StandardCharsets.UTF_8));
        });
        URI flakyBaseUri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/flaky/");
        MavenRepositoryClient retryingClient = retryingClient(3);

        RepositoryArtifact artifact = retryingClient.fetchPom(flakyBaseUri, coordinate);

        assertEquals(2, attempts.get());
        assertArrayEquals("<project/>".getBytes(StandardCharsets.UTF_8), artifact.bytes());
    }

    @Test
    void transientStatusFailsAfterBoundedAttempts() {
        Coordinate coordinate = parser.parse("com.google.guava:guava:33.4.0-jre");
        AtomicInteger attempts = new AtomicInteger();
        server.createContext("/always-flaky/", exchange -> {
            attempts.incrementAndGet();
            respond(exchange, 429, "too many".getBytes(StandardCharsets.UTF_8));
        });
        URI flakyBaseUri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/always-flaky/");
        MavenRepositoryClient retryingClient = retryingClient(2);

        RepositoryClientException exception = assertThrows(
                RepositoryClientException.class,
                () -> retryingClient.fetchPom(flakyBaseUri, coordinate));

        assertEquals(2, attempts.get());
        assertTrue(exception.getMessage().contains("Repository returned HTTP 429"));
        assertTrue(exception.getMessage().contains("after 2 attempts"));
    }

    @Test
    void permanentClientStatusIsNotRetried() {
        Coordinate coordinate = parser.parse("com.google.guava:guava:33.4.0-jre");
        AtomicInteger attempts = new AtomicInteger();
        server.createContext("/bad-request/", exchange -> {
            attempts.incrementAndGet();
            respond(exchange, 400, "bad".getBytes(StandardCharsets.UTF_8));
        });
        URI badRequestBaseUri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/bad-request/");
        MavenRepositoryClient retryingClient = retryingClient(3);

        RepositoryClientException exception = assertThrows(
                RepositoryClientException.class,
                () -> retryingClient.fetchPom(badRequestBaseUri, coordinate));

        assertEquals(1, attempts.get());
        assertTrue(exception.getMessage().contains("Repository returned HTTP 400"));
    }

    @Test
    void sendsBasicAuthenticationHeaderWhenConfigured() {
        Coordinate coordinate = parser.parse("com.google.guava:guava:33.4.0-jre");
        AtomicInteger attempts = new AtomicInteger();
        server.createContext("/auth/", exchange -> {
            attempts.incrementAndGet();
            String expected = "Basic " + Base64.getEncoder()
                    .encodeToString("zolt-user:zolt-secret".getBytes(StandardCharsets.UTF_8));
            if (!expected.equals(exchange.getRequestHeaders().getFirst("Authorization"))) {
                respond(exchange, 401, "unauthorized".getBytes(StandardCharsets.UTF_8));
                return;
            }
            respond(exchange, 200, "<project/>".getBytes(StandardCharsets.UTF_8));
        });
        URI authBaseUri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/auth/");

        RepositoryArtifact artifact = client.fetchPom(
                authBaseUri,
                coordinate,
                Optional.of(new RepositoryAuthentication("zolt-user", "zolt-secret")));

        assertEquals(1, attempts.get());
        assertArrayEquals("<project/>".getBytes(StandardCharsets.UTF_8), artifact.bytes());
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

    private void put(String path, String body) {
        responses.put("/maven2/" + path, body.getBytes(StandardCharsets.UTF_8));
    }

    private void handle(HttpExchange exchange) throws IOException {
        requestCounts
                .computeIfAbsent(exchange.getRequestURI().getPath(), ignored -> new AtomicInteger())
                .incrementAndGet();
        byte[] body = responses.get(exchange.getRequestURI().getPath());
        if (body == null) {
            respond(exchange, 404, "missing".getBytes(StandardCharsets.UTF_8));
            return;
        }
        respond(exchange, 200, body);
    }

    private int requestCount(String path) {
        return requestCounts.getOrDefault(path, new AtomicInteger()).get();
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
