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
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Exercises the polling loop against a fixture Portal that returns a scripted sequence of deployment
 * states. Time is driven by a mutable clock that only advances when the injected sleeper is asked to
 * pause, so the loop runs to completion without real delays.
 */
final class CentralDeploymentWaiterTest {
    private static final Duration POLL_INTERVAL = Duration.ofSeconds(5);
    private static final String TOKEN = "dG9rZW4=";
    private static final String DEPLOYMENT_ID = "dep-1";

    private HttpServer server;
    private String baseUrl;
    private final AtomicInteger statusCalls = new AtomicInteger();

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
    void pollsThroughTransientStatesUntilPublished() {
        // VALIDATED in the middle proves it is transient for an automatic deployment.
        scriptStates("PENDING", "VALIDATED", "PUBLISHING", "PUBLISHED");
        CentralDeploymentWaiter waiter = waiter();

        CentralDeploymentStatus status = waiter.awaitTerminal(
                baseUrl, DEPLOYMENT_ID, TOKEN, CentralPublishingType.AUTOMATIC, Duration.ofMinutes(10));

        assertEquals("PUBLISHED", status.state());
        assertEquals(4, statusCalls.get());
    }

    @Test
    void userManagedValidatedIsTerminal() {
        scriptStates("PENDING", "VALIDATING", "VALIDATED", "PUBLISHED");
        CentralDeploymentWaiter waiter = waiter();

        CentralDeploymentStatus status = waiter.awaitTerminal(
                baseUrl, DEPLOYMENT_ID, TOKEN, CentralPublishingType.USER_MANAGED, Duration.ofMinutes(10));

        // Stops at VALIDATED (the user finishes the release in the Portal); never reaches PUBLISHED.
        assertEquals("VALIDATED", status.state());
        assertEquals(3, statusCalls.get());
    }

    @Test
    void failedDeploymentRaisesActionableErrorWithReportedDetail() {
        AtomicInteger index = new AtomicInteger();
        List<String> bodies = List.of(
                "{\"deploymentId\":\"dep-1\",\"deploymentState\":\"PENDING\"}",
                "{\"deploymentId\":\"dep-1\",\"deploymentState\":\"FAILED\","
                        + "\"errors\":{\"dep-1\":[\"Missing signature for artifact foo-1.0.jar.asc\"]}}");
        server.createContext("/api/v1/publisher/status", exchange -> {
            statusCalls.incrementAndGet();
            respond(exchange, 200, bodies.get(Math.min(index.getAndIncrement(), bodies.size() - 1)));
        });
        CentralDeploymentWaiter waiter = waiter();

        PublishException exception = assertThrows(PublishException.class, () -> waiter.awaitTerminal(
                baseUrl, DEPLOYMENT_ID, TOKEN, CentralPublishingType.AUTOMATIC, Duration.ofMinutes(10)));

        assertTrue(exception.getMessage().contains("FAILED"), exception.getMessage());
        assertTrue(exception.getMessage().contains(DEPLOYMENT_ID), exception.getMessage());
        assertTrue(exception.getMessage().contains("Next:"), exception.getMessage());
        assertTrue(exception.getMessage().contains("Missing signature for artifact foo-1.0.jar.asc"),
                exception.getMessage());
        assertEquals(2, statusCalls.get());
    }

    @Test
    void timesOutNamingTheDeploymentWhenNeverTerminal() {
        server.createContext("/api/v1/publisher/status", exchange -> {
            statusCalls.incrementAndGet();
            respond(exchange, 200, "{\"deploymentId\":\"dep-1\",\"deploymentState\":\"PENDING\"}");
        });
        CentralDeploymentWaiter waiter = waiter();

        PublishException exception = assertThrows(PublishException.class, () -> waiter.awaitTerminal(
                baseUrl, DEPLOYMENT_ID, TOKEN, CentralPublishingType.AUTOMATIC, Duration.ofSeconds(12)));

        assertTrue(exception.getMessage().contains("Timed out"), exception.getMessage());
        assertTrue(exception.getMessage().contains("12s"), exception.getMessage());
        assertTrue(exception.getMessage().contains(DEPLOYMENT_ID), exception.getMessage());
        assertTrue(exception.getMessage().contains("Next:"), exception.getMessage());
        assertFalse(exception.getMessage().contains(TOKEN), exception.getMessage());
    }

    private void scriptStates(String... states) {
        List<String> sequence = List.of(states);
        AtomicInteger index = new AtomicInteger();
        server.createContext("/api/v1/publisher/status", exchange -> {
            statusCalls.incrementAndGet();
            String state = sequence.get(Math.min(index.getAndIncrement(), sequence.size() - 1));
            respond(exchange, 200, "{\"deploymentId\":\"dep-1\",\"deploymentState\":\"" + state + "\"}");
        });
    }

    private CentralDeploymentWaiter waiter() {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-22T00:00:00Z"));
        CentralPollSleeper sleeper = clock::advance;
        return new CentralDeploymentWaiter(
                new CentralPortalClient(NetworkTransport.direct()), clock, sleeper, POLL_INTERVAL);
    }

    private static void respond(HttpExchange exchange, int statusCode, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        try (exchange) {
            exchange.sendResponseHeaders(statusCode, bytes.length);
            exchange.getResponseBody().write(bytes);
        }
    }

    /** A {@link Clock} that stands still until {@link #advance(Duration)} moves it forward. */
    private static final class MutableClock extends Clock {
        private final AtomicReference<Instant> now;
        private final ZoneId zone;

        private MutableClock(Instant start) {
            this(new AtomicReference<>(start), ZoneOffset.UTC);
        }

        private MutableClock(AtomicReference<Instant> now, ZoneId zone) {
            this.now = now;
            this.zone = zone;
        }

        void advance(Duration amount) {
            now.updateAndGet(instant -> instant.plus(amount));
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId newZone) {
            return new MutableClock(now, newZone);
        }

        @Override
        public Instant instant() {
            return now.get();
        }
    }
}
