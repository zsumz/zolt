package com.zolt.resolve.support;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

public abstract class ResolveServiceRepositoryTestSupport extends ResolveServiceRepositoryArtifactSupport {
    protected final Map<String, byte[]> responses = new HashMap<>();
    protected final Set<String> slowPomPaths = ConcurrentHashMap.newKeySet();
    protected final Set<String> slowArtifactPaths = ConcurrentHashMap.newKeySet();
    protected final Map<String, AtomicInteger> requestCounts = new ConcurrentHashMap<>();
    protected final AtomicInteger totalRequests = new AtomicInteger();
    protected final AtomicInteger activePomRequests = new AtomicInteger();
    protected final AtomicInteger maxPomRequests = new AtomicInteger();
    protected final AtomicInteger activeArtifactRequests = new AtomicInteger();
    protected final AtomicInteger maxArtifactRequests = new AtomicInteger();
    private final Object slowPomMonitor = new Object();
    private final Object slowArtifactMonitor = new Object();

    @TempDir
    protected Path tempDir;

    protected HttpServer server;
    private ExecutorService serverExecutor;
    protected URI baseUri;

    @BeforeEach
    void startServer() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException exception) {
            assumeTrue(false, "local HTTP server sockets are unavailable: " + exception.getMessage());
            return;
        }
        server.createContext("/", this::handle);
        serverExecutor = Executors.newCachedThreadPool();
        server.setExecutor(serverExecutor);
        server.start();
        baseUri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/maven2/");
        addArtifact("com.example", "app", "1.0.0", """
                <project>
                  <groupId>com.example</groupId>
                  <artifactId>app</artifactId>
                  <version>1.0.0</version>
                  <dependencies>
                    <dependency>
                      <groupId>com.example</groupId>
                      <artifactId>lib</artifactId>
                      <version>1.0.0</version>
                    </dependency>
                  </dependencies>
                </project>
                """);
        addArtifact("com.example", "lib", "1.0.0", """
                <project>
                  <groupId>com.example</groupId>
                  <artifactId>lib</artifactId>
                  <version>1.0.0</version>
                </project>
                """);
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
        if (serverExecutor != null) {
            serverExecutor.shutdownNow();
        }
    }

    protected void addArtifact(String groupId, String artifactId, String version, String pom) {
        addArtifact(responses, groupId, artifactId, version, pom);
    }

    protected void addArtifact(
            String groupId,
            String artifactId,
            String version,
            String pom,
            Map<String, String> jarEntries) {
        addArtifact(responses, groupId, artifactId, version, pom, jarEntries);
    }

    protected void addClassifierJar(
            String groupId,
            String artifactId,
            String version,
            String classifier,
            Map<String, String> jarEntries) {
        addClassifierJar(responses, groupId, artifactId, version, classifier, jarEntries);
    }

    protected void addArtifact(
            String groupId,
            String artifactId,
            String version,
            String extension,
            String content) {
        addPom(responses, groupId, artifactId, version, """
                <project>
                  <groupId>%s</groupId>
                  <artifactId>%s</artifactId>
                  <version>%s</version>
                </project>
                """.formatted(groupId, artifactId, version));
        responses.put(
                "/maven2/" + groupId.replace('.', '/') + "/" + artifactId + "/" + version + "/" + artifactId + "-" + version + "." + extension,
                content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    protected void addJUnitConsoleArtifact(String version) {
        addArtifact("org.junit.platform", "junit-platform-console", version, """
                <project>
                  <groupId>org.junit.platform</groupId>
                  <artifactId>junit-platform-console</artifactId>
                  <version>%s</version>
                </project>
                """.formatted(version));
    }

    protected void addPom(String groupId, String artifactId, String version, String pom) {
        addPom(responses, groupId, artifactId, version, pom);
    }

    protected void resetRequestCounts() {
        requestCounts.clear();
        totalRequests.set(0);
    }

    protected int requestCount(String path) {
        return requestCounts.getOrDefault(path, new AtomicInteger()).get();
    }

    private void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        requestCounts.computeIfAbsent(path, ignored -> new AtomicInteger()).incrementAndGet();
        totalRequests.incrementAndGet();
        if (slowPomPaths.contains(path)) {
            int active = activePomRequests.incrementAndGet();
            maxPomRequests.accumulateAndGet(active, Math::max);
            try {
                awaitConcurrentPeer(maxPomRequests, slowPomPaths.size(), slowPomMonitor);
            } finally {
                activePomRequests.decrementAndGet();
                notifyPeers(slowPomMonitor);
            }
        }
        if (slowArtifactPaths.contains(path)) {
            int active = activeArtifactRequests.incrementAndGet();
            maxArtifactRequests.accumulateAndGet(active, Math::max);
            try {
                awaitConcurrentPeer(maxArtifactRequests, slowArtifactPaths.size(), slowArtifactMonitor);
            } finally {
                activeArtifactRequests.decrementAndGet();
                notifyPeers(slowArtifactMonitor);
            }
        }
        byte[] body = responses.get(path);
        if (body == null) {
            respond(exchange, 404, "missing".getBytes(StandardCharsets.UTF_8));
            return;
        }
        respond(exchange, 200, body);
    }

    private static void awaitConcurrentPeer(
            AtomicInteger maxRequests,
            int expectedSlowPaths,
            Object monitor) throws IOException {
        int target = Math.min(2, expectedSlowPaths);
        if (target < 2) {
            return;
        }
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(2);
        synchronized (monitor) {
            monitor.notifyAll();
            while (maxRequests.get() < target) {
                long remainingNanos = deadline - System.nanoTime();
                if (remainingNanos <= 0) {
                    throw new IOException("Timed out waiting for concurrent fake repository requests.");
                }
                try {
                    monitor.wait(Math.max(1L, java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(remainingNanos)));
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted while waiting for concurrent fake repository requests.", exception);
                }
            }
        }
    }

    private static void notifyPeers(Object monitor) {
        synchronized (monitor) {
            monitor.notifyAll();
        }
    }

    private static void respond(HttpExchange exchange, int statusCode, byte[] body) throws IOException {
        try (exchange) {
            exchange.sendResponseHeaders(statusCode, body.length);
            exchange.getResponseBody().write(body);
        }
    }
}
