package sh.zolt.workspace.publish;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A test double for an immutable Maven repository: it stores PUTs, serves GETs (so the idempotency
 * probe can confirm what landed), 409s a re-PUT of a stored path, records the auth header seen per
 * path, and counts PUTs per path. {@code failPutPathSuffix} rejects a chosen PUT once so a mid-member
 * failure can be injected.
 */
final class PublishFixtureRepository {
    private final HttpServer server;
    final Map<String, byte[]> store = new ConcurrentHashMap<>();
    private final Map<String, Integer> putCounts = new ConcurrentHashMap<>();
    final Map<String, String> authByPath = new ConcurrentHashMap<>();
    private final String baseUri;
    volatile String failPutPathSuffix;

    private PublishFixtureRepository(HttpServer server) {
        this.server = server;
        this.baseUri = "http://127.0.0.1:" + server.getAddress().getPort() + "/maven2/";
    }

    static PublishFixtureRepository start() {
        HttpServer server;
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException exception) {
            assumeTrue(false, "local HTTP server sockets are unavailable: " + exception.getMessage());
            throw new IllegalStateException(exception);
        }
        PublishFixtureRepository repository = new PublishFixtureRepository(server);
        server.createContext("/", repository::handle);
        server.start();
        return repository;
    }

    String baseUri() {
        return baseUri;
    }

    boolean has(String path) {
        return store.containsKey(path);
    }

    int putCount(String path) {
        return putCounts.getOrDefault(path, 0);
    }

    private void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String authorization = exchange.getRequestHeaders().getFirst("Authorization");
        authByPath.put(path, authorization == null ? "<none>" : authorization);
        if ("PUT".equals(exchange.getRequestMethod())) {
            byte[] body = exchange.getRequestBody().readAllBytes();
            putCounts.merge(path, 1, Integer::sum);
            String suffix = failPutPathSuffix;
            if (suffix != null && path.endsWith(suffix)) {
                respond(exchange, 403, new byte[0]);
                return;
            }
            if (store.containsKey(path)) {
                respond(exchange, 409, new byte[0]);
                return;
            }
            store.put(path, body);
            respond(exchange, 201, new byte[0]);
            return;
        }
        byte[] body = store.get(path);
        if (body == null) {
            respond(exchange, 404, "missing".getBytes(StandardCharsets.UTF_8));
            return;
        }
        respond(exchange, 200, body);
    }

    private static void respond(HttpExchange exchange, int status, byte[] body) throws IOException {
        try (exchange) {
            exchange.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
            if (body.length > 0) {
                exchange.getResponseBody().write(body);
            }
        }
    }

    void close() {
        server.stop(0);
    }
}
