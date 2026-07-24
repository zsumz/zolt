package sh.zolt.maven.repository;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** {@link MavenRepositoryClient#fetchFile} — the idempotency probe for a transactional re-PUT. */
final class MavenRepositoryClientFetchFileTest extends MavenRepositoryClientTestSupport {
    @Test
    void returnsStoredBytesForAnExplicitPath() {
        put("com/acme/acme-core/1.0.0/acme-core-1.0.0.jar.sha256", "deadbeef");

        Optional<byte[]> body = client.fetchFile(
                baseUri, "com/acme/acme-core/1.0.0/acme-core-1.0.0.jar.sha256", RepositoryAuthentication.none());

        assertTrue(body.isPresent());
        assertArrayEquals("deadbeef".getBytes(StandardCharsets.UTF_8), body.orElseThrow());
        assertEquals(1, requestCount("/maven2/com/acme/acme-core/1.0.0/acme-core-1.0.0.jar.sha256"));
    }

    @Test
    void returnsEmptyWhenThePathIsAbsent() {
        Optional<byte[]> body = client.fetchFile(
                baseUri, "com/acme/acme-core/1.0.0/acme-core-1.0.0.jar.asc", RepositoryAuthentication.none());

        assertFalse(body.isPresent());
        assertEquals(1, requestCount("/maven2/com/acme/acme-core/1.0.0/acme-core-1.0.0.jar.asc"));
    }

    @Test
    void retriesTransientFailures() {
        AtomicInteger attempts = new AtomicInteger();
        server.createContext("/retry/com/acme/lib-1.0.0.pom", exchange -> {
            if (attempts.incrementAndGet() == 1) {
                respond(exchange, 503, "unavailable".getBytes(StandardCharsets.UTF_8));
                return;
            }
            respond(exchange, 200, "<project/>".getBytes(StandardCharsets.UTF_8));
        });
        URI retryBaseUri = URI.create(baseUri.toString().replace("/maven2/", "/retry/"));

        Optional<byte[]> body =
                retryingClient(3).fetchFile(retryBaseUri, "com/acme/lib-1.0.0.pom", RepositoryAuthentication.none());

        assertTrue(body.isPresent());
        assertEquals(2, attempts.get());
    }
}
