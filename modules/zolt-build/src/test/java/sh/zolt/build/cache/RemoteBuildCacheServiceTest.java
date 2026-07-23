package sh.zolt.build.cache;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import sh.zolt.maven.repository.RepositoryAuthentication;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Exercises the remote HTTP build-cache tier against an in-process dumb blob server. */
final class RemoteBuildCacheServiceTest {
    private static final BuildCacheKey KEY = BuildCacheKey.of(BuildCacheScope.MAIN, "inputsha", "21");

    private FixtureServer server;

    @BeforeEach
    void startServer() throws IOException {
        server = new FixtureServer();
    }

    @AfterEach
    void stopServer() {
        server.stop();
    }

    @Test
    void remoteHitPopulatesLocalCacheAndRestores(@TempDir Path temp) throws IOException {
        byte[] classBytes = "restored-from-remote".getBytes(StandardCharsets.UTF_8);
        seedRemoteFrom(temp, classBytes);

        BuildCacheService consumer = consumer(temp, remoteClient(temp, Optional.empty(), false));
        Path output = temp.resolve("consumer/classes");
        BuildCacheRestoreResult result = consumer.restore(KEY, output);

        assertTrue(result.restored());
        assertEquals("remote", result.source());
        assertArrayEquals(classBytes, Files.readAllBytes(output.resolve("com/example/A.class")));
        // The remote hit was copied into the local store, so a second restore serves locally.
        assertEquals("local", consumer.restore(KEY, temp.resolve("consumer2/classes")).source());
    }

    @Test
    void remoteMissRebuilds(@TempDir Path temp) {
        BuildCacheService consumer = consumer(temp, remoteClient(temp, Optional.empty(), false));
        assertFalse(consumer.restore(KEY, temp.resolve("out")).restored());
    }

    @Test
    void pushUploadsBlobAndMetadataWithBearerAuth(@TempDir Path temp) throws IOException {
        BuildCacheService producer = consumer(temp,
                remoteClient(temp, Optional.of(RepositoryAuthentication.bearer("secret-token")), true));
        Path output = temp.resolve("producer/classes");
        writeClass(output);

        producer.store(KEY, output);

        assertTrue(server.store.containsKey("/" + KEY.shardedPath(".zbc")), "blob uploaded");
        assertTrue(server.store.containsKey("/" + KEY.shardedPath(".zbc.meta")), "metadata uploaded");
        assertTrue(server.authHeaders.contains("Bearer secret-token"), "bearer auth header sent");
        assertTrue(producer.drainWarnings().isEmpty());
    }

    @Test
    void pushSendsBasicAuthHeader(@TempDir Path temp) throws IOException {
        BuildCacheService producer = consumer(temp,
                remoteClient(temp, Optional.of(new RepositoryAuthentication("user", "pass")), true));
        writeClass(temp.resolve("producer/classes"));
        producer.store(KEY, temp.resolve("producer/classes"));
        assertTrue(
                server.authHeaders.stream().anyMatch(header -> header.startsWith("Basic ")),
                "basic auth header sent");
    }

    @Test
    void uploadFailureWarnsAndContinues(@TempDir Path temp) throws IOException {
        server.putStatus = 500;
        BuildCacheService producer = consumer(temp, remoteClient(temp, Optional.empty(), true));
        writeClass(temp.resolve("producer/classes"));

        producer.store(KEY, temp.resolve("producer/classes"));

        // The store still succeeded locally, and the failure surfaced only as a warning.
        assertTrue(producer.localCache().orElseThrow().archiveFileIfPresent(KEY).isPresent());
        List<String> warnings = producer.drainWarnings();
        assertEquals(1, warnings.size());
        assertTrue(warnings.getFirst().contains("failed"));
    }

    @Test
    void unauthorizedPushSurfacesActionableWarning(@TempDir Path temp) throws IOException {
        server.putStatus = 401;
        BuildCacheService producer = consumer(temp, remoteClient(temp, Optional.empty(), true));
        writeClass(temp.resolve("producer/classes"));

        producer.store(KEY, temp.resolve("producer/classes"));

        List<String> warnings = producer.drainWarnings();
        assertEquals(1, warnings.size());
        assertTrue(warnings.getFirst().contains("unauthorized"));
    }

    @Test
    void noPushWhenPushDisabled(@TempDir Path temp) throws IOException {
        BuildCacheService producer = consumer(temp, remoteClient(temp, Optional.empty(), false));
        writeClass(temp.resolve("producer/classes"));
        producer.store(KEY, temp.resolve("producer/classes"));
        assertTrue(server.store.isEmpty(), "no upload happens when push is disabled");
    }

    @Test
    void offlineServiceHasNoRemoteTier(@TempDir Path temp) {
        // Offline is modeled as no remote client; the local cache still serves and the server is untouched.
        BuildCacheService offline = BuildCacheService.create(settings(temp), Optional.empty(), "test");
        assertFalse(offline.restore(KEY, temp.resolve("out")).restored());
        assertTrue(server.store.isEmpty());
    }

    private void seedRemoteFrom(Path temp, byte[] classBytes) throws IOException {
        Path output = temp.resolve("origin/classes");
        writeClass(output, classBytes);
        LocalBuildCache origin = new LocalBuildCache(temp.resolve("origin-cache"), 0L);
        origin.store(KEY, output, "producer");
        server.store.put("/" + KEY.shardedPath(".zbc"),
                Files.readAllBytes(origin.archiveFileIfPresent(KEY).orElseThrow()));
        server.store.put("/" + KEY.shardedPath(".zbc.meta"),
                Files.readAllBytes(origin.metaFileIfPresent(KEY).orElseThrow()));
    }

    private BuildCacheService consumer(Path temp, RemoteBuildCacheClient remote) {
        return BuildCacheService.create(settings(temp), Optional.of(remote), "test");
    }

    private static BuildCacheSettings settings(Path temp) {
        return new BuildCacheSettings(true, temp.resolve("local-cache"), 0L);
    }

    private RemoteBuildCacheClient remoteClient(
            Path temp, Optional<RepositoryAuthentication> auth, boolean push) {
        return new RemoteBuildCacheClient(HttpClient.newHttpClient(), server.baseUri(), auth, push);
    }

    private static void writeClass(Path output) throws IOException {
        writeClass(output, "class-bytes".getBytes(StandardCharsets.UTF_8));
    }

    private static void writeClass(Path output, byte[] bytes) throws IOException {
        Path classFile = output.resolve("com/example/A.class");
        Files.createDirectories(classFile.getParent());
        Files.write(classFile, bytes);
    }

    private static final class FixtureServer {
        private final HttpServer server;
        final Map<String, byte[]> store = new ConcurrentHashMap<>();
        final List<String> authHeaders = new CopyOnWriteArrayList<>();
        volatile int putStatus = 201;

        FixtureServer() throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                String auth = exchange.getRequestHeaders().getFirst("Authorization");
                if (auth != null) {
                    authHeaders.add(auth);
                }
                String path = exchange.getRequestURI().getPath();
                if ("GET".equals(exchange.getRequestMethod())) {
                    byte[] body = store.get(path);
                    if (body == null) {
                        exchange.sendResponseHeaders(404, -1);
                    } else {
                        exchange.sendResponseHeaders(200, body.length);
                        try (OutputStream out = exchange.getResponseBody()) {
                            out.write(body);
                        }
                    }
                } else if ("PUT".equals(exchange.getRequestMethod())) {
                    byte[] body = exchange.getRequestBody().readAllBytes();
                    if (putStatus >= 200 && putStatus < 300) {
                        store.put(path, body);
                    }
                    exchange.sendResponseHeaders(putStatus, -1);
                } else {
                    exchange.sendResponseHeaders(405, -1);
                }
                exchange.close();
            });
            server.start();
        }

        URI baseUri() {
            return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/");
        }

        void stop() {
            server.stop(0);
        }
    }
}
