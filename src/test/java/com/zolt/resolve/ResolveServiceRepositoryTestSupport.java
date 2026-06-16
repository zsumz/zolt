package com.zolt.resolve;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

abstract class ResolveServiceRepositoryTestSupport {
    final Map<String, byte[]> responses = new HashMap<>();
    final Set<String> slowPomPaths = ConcurrentHashMap.newKeySet();
    final Set<String> slowArtifactPaths = ConcurrentHashMap.newKeySet();
    final Map<String, Long> responseDelayMillis = new ConcurrentHashMap<>();
    final Map<String, AtomicInteger> requestCounts = new ConcurrentHashMap<>();
    final AtomicInteger totalRequests = new AtomicInteger();
    final AtomicInteger activePomRequests = new AtomicInteger();
    final AtomicInteger maxPomRequests = new AtomicInteger();
    final AtomicInteger activeArtifactRequests = new AtomicInteger();
    final AtomicInteger maxArtifactRequests = new AtomicInteger();

    @TempDir
    Path tempDir;

    HttpServer server;
    private ExecutorService serverExecutor;
    URI baseUri;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
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
        server.stop(0);
        serverExecutor.shutdownNow();
    }

    void addArtifact(String groupId, String artifactId, String version, String pom) {
        addArtifact(groupId, artifactId, version, pom, Map.of());
    }

    void addArtifact(
            String groupId,
            String artifactId,
            String version,
            String pom,
            Map<String, String> jarEntries) {
        String base = "/maven2/" + groupId.replace('.', '/') + "/" + artifactId + "/" + version + "/" + artifactId + "-" + version;
        responses.put(base + ".pom", pom.getBytes(StandardCharsets.UTF_8));
        responses.put(base + ".jar", jarBytes(jarEntries));
    }

    static String simplePom(String groupId, String artifactId, String version) {
        return """
                <project>
                  <groupId>%s</groupId>
                  <artifactId>%s</artifactId>
                  <version>%s</version>
                </project>
                """.formatted(groupId, artifactId, version);
    }

    static String jarRepositoryPath(String groupId, String artifactId, String version) {
        return "/maven2/"
                + groupId.replace('.', '/')
                + "/"
                + artifactId
                + "/"
                + version
                + "/"
                + artifactId
                + "-"
                + version
                + ".jar";
    }

    static String pomRepositoryPath(String groupId, String artifactId, String version) {
        return "/maven2/"
                + groupId.replace('.', '/')
                + "/"
                + artifactId
                + "/"
                + version
                + "/"
                + artifactId
                + "-"
                + version
                + ".pom";
    }

    void setResponseDelays(Map<String, Long> artifactDelaysMillis) {
        artifactDelaysMillis.forEach((artifactId, delayMillis) -> {
            responseDelayMillis.put(pomRepositoryPath("com.example", artifactId, "1.0.0"), delayMillis);
            responseDelayMillis.put(jarRepositoryPath("com.example", artifactId, "1.0.0"), delayMillis);
        });
    }

    void resetRequestCounts() {
        requestCounts.clear();
        totalRequests.set(0);
    }

    void addClassifierJar(
            String groupId,
            String artifactId,
            String version,
            String classifier,
            Map<String, String> jarEntries) {
        String base = "/maven2/" + groupId.replace('.', '/') + "/" + artifactId + "/" + version + "/" + artifactId + "-" + version;
        responses.put(base + "-" + classifier + ".jar", jarBytes(jarEntries));
    }

    void addArtifact(
            String groupId,
            String artifactId,
            String version,
            String extension,
            String content) {
        String base = "/maven2/" + groupId.replace('.', '/') + "/" + artifactId + "/" + version + "/" + artifactId + "-" + version;
        responses.put(base + ".pom", """
                <project>
                  <groupId>%s</groupId>
                  <artifactId>%s</artifactId>
                  <version>%s</version>
                </project>
                """.formatted(groupId, artifactId, version).getBytes(StandardCharsets.UTF_8));
        responses.put(base + "." + extension, content.getBytes(StandardCharsets.UTF_8));
    }

    void writeLocalArtifact(
            Path root,
            String groupId,
            String artifactId,
            String version,
            String pom,
            Map<String, String> jarEntries) {
        String base = groupId.replace('.', '/') + "/" + artifactId + "/" + version + "/" + artifactId + "-" + version;
        writeFile(root.resolve(base + ".pom"), pom.getBytes(StandardCharsets.UTF_8));
        writeFile(root.resolve(base + ".jar"), jarBytes(jarEntries));
    }

    static void writeFile(Path path, byte[] bytes) {
        try {
            Files.createDirectories(path.getParent());
            Files.write(path, bytes);
        } catch (IOException exception) {
            throw new AssertionError("Could not write test file " + path, exception);
        }
    }

    void addJUnitConsoleArtifact(String version) {
        addArtifact("org.junit.platform", "junit-platform-console", version, """
                <project>
                  <groupId>org.junit.platform</groupId>
                  <artifactId>junit-platform-console</artifactId>
                  <version>%s</version>
                </project>
                """.formatted(version));
    }

    void addPom(String groupId, String artifactId, String version, String pom) {
        String base = "/maven2/" + groupId.replace('.', '/') + "/" + artifactId + "/" + version + "/" + artifactId + "-" + version;
        responses.put(base + ".pom", pom.getBytes(StandardCharsets.UTF_8));
    }

    static byte[] jarBytes(Map<String, String> entries) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (JarOutputStream jar = new JarOutputStream(bytes)) {
                for (Map.Entry<String, String> entry : entries.entrySet()) {
                    jar.putNextEntry(new JarEntry(entry.getKey()));
                    jar.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                    jar.closeEntry();
                }
            }
            return bytes.toByteArray();
        } catch (IOException exception) {
            throw new AssertionError("Could not create test jar bytes.", exception);
        }
    }

    int requestCount(String path) {
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
                sleepServing(150L);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while serving slow test POM.", exception);
            } finally {
                activePomRequests.decrementAndGet();
            }
        }
        if (slowArtifactPaths.contains(path)) {
            int active = activeArtifactRequests.incrementAndGet();
            maxArtifactRequests.accumulateAndGet(active, Math::max);
            try {
                sleepServing(150L);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while serving slow test artifact.", exception);
            } finally {
                activeArtifactRequests.decrementAndGet();
            }
        }
        Long delayMillis = responseDelayMillis.get(path);
        if (delayMillis != null && delayMillis > 0) {
            try {
                sleepServing(delayMillis);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while serving delayed test response.", exception);
            }
        }
        byte[] body = responses.get(path);
        if (body == null) {
            respond(exchange, 404, "missing".getBytes(StandardCharsets.UTF_8));
            return;
        }
        respond(exchange, 200, body);
    }

    private static void sleepServing(long millis) throws InterruptedException {
        Thread.sleep(millis);
    }

    private static void respond(HttpExchange exchange, int statusCode, byte[] body) throws IOException {
        try (exchange) {
            exchange.sendResponseHeaders(statusCode, body.length);
            exchange.getResponseBody().write(body);
        }
    }
}
