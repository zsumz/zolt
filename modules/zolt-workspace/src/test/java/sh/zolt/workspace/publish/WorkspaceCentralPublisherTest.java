package sh.zolt.workspace.publish;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.sun.net.httpserver.HttpServer;
import sh.zolt.net.NetworkTransport;
import sh.zolt.publish.CentralDeploymentWaiter;
import sh.zolt.publish.CentralPollSleeper;
import sh.zolt.publish.CentralPortalClient;
import sh.zolt.publish.CentralPublishingType;
import sh.zolt.publish.PublishArtifactPlan;
import sh.zolt.publish.PublishCentralSettings;
import sh.zolt.publish.PublishDryRunPlan;
import sh.zolt.publish.PublishSettings;
import sh.zolt.publish.PublishSigningSettings;
import sh.zolt.workspace.WorkspaceConfig;
import sh.zolt.workspace.service.Workspace;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Regression: the Central family path assembles ONE bundle with every member and honours --wait. */
final class WorkspaceCentralPublisherTest {

    @Test
    void uploadsOneAtomicFamilyBundleContainingEveryMemberArtifactAndPom(@TempDir Path tempDir) throws IOException {
        HttpServer server = startServer();
        AtomicInteger uploads = new AtomicInteger();
        AtomicReference<String> uploadAuth = new AtomicReference<>();
        server.createContext("/api/v1/publisher/upload", exchange -> {
            uploads.incrementAndGet();
            uploadAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            exchange.getRequestBody().readAllBytes();
            respond(exchange, 201, "dep-9001");
        });
        server.createContext("/api/v1/publisher/status", exchange -> respond(exchange, 200, statusJson("PUBLISHED")));
        server.start();
        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            List<MemberPublication> family = family(tempDir, baseUrl, PublishSigningSettings.disabled());
            Workspace workspace = workspace(tempDir);
            WorkspaceCentralPublisher publisher = new WorkspaceCentralPublisher(
                    new CentralPortalClient(NetworkTransport.direct()),
                    new CentralDeploymentWaiter(new CentralPortalClient(NetworkTransport.direct())),
                    name -> "ZOLT_PORTAL_TOKEN".equals(name) ? "portal-secret" : null);

            WorkspacePublishReport report = publisher.publish(
                    workspace, family, new WorkspacePublishService.Options(false, true, false, Optional.empty()));

            assertTrue(report.ok(), () -> "blockers: " + report.blockers());
            assertTrue(report.uploaded());
            assertEquals(Optional.of("dep-9001"), report.deploymentId());
            assertEquals(1, uploads.get(), "one atomic deployment for the whole family");
            assertEquals("Bearer portal-secret", uploadAuth.get());

            Set<String> entries = bundleEntries(tempDir.resolve("target/publish/central-bundle.zip"));
            // Every member's main artifact, its supplementals, and its POM — plus the BOM POM.
            assertTrue(entries.contains("com/acme/acme-core/1.0.0/acme-core-1.0.0.jar"), entries::toString);
            assertTrue(entries.contains("com/acme/acme-core/1.0.0/acme-core-1.0.0-sources.jar"), entries::toString);
            assertTrue(entries.contains("com/acme/acme-core/1.0.0/acme-core-1.0.0-javadoc.jar"), entries::toString);
            assertTrue(entries.contains("com/acme/acme-core/1.0.0/acme-core-1.0.0-cyclonedx.json"), entries::toString);
            assertTrue(entries.contains("com/acme/acme-core/1.0.0/acme-core-1.0.0.pom"), entries::toString);
            assertTrue(entries.contains("com/acme/acme-http/1.0.0/acme-http-1.0.0.jar"), entries::toString);
            assertTrue(entries.contains("com/acme/acme-http/1.0.0/acme-http-1.0.0-sources.jar"), entries::toString);
            assertTrue(entries.contains("com/acme/acme-http/1.0.0/acme-http-1.0.0.pom"), entries::toString);
            assertTrue(entries.contains("com/acme/platform/acme-bom/1.0.0/acme-bom-1.0.0.pom"), entries::toString);
            // Checksums ride along for every entry.
            assertTrue(entries.contains("com/acme/acme-core/1.0.0/acme-core-1.0.0.jar.sha256"), entries::toString);
            assertTrue(entries.contains("com/acme/platform/acme-bom/1.0.0/acme-bom-1.0.0.pom.md5"), entries::toString);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void waitPollsTheStatusEndpointThroughTransientStatesToATerminalState(@TempDir Path tempDir) throws IOException {
        HttpServer server = startServer();
        AtomicInteger statusCalls = new AtomicInteger();
        List<String> states = List.of("PENDING", "VALIDATING", "PUBLISHED");
        server.createContext("/api/v1/publisher/upload", exchange -> {
            exchange.getRequestBody().readAllBytes();
            respond(exchange, 201, "dep-42");
        });
        server.createContext("/api/v1/publisher/status", exchange -> {
            int index = Math.min(statusCalls.getAndIncrement(), states.size() - 1);
            respond(exchange, 200, statusJson(states.get(index)));
        });
        server.start();
        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            List<MemberPublication> family = family(tempDir, baseUrl, PublishSigningSettings.disabled());
            Workspace workspace = workspace(tempDir);
            // Inject the shared waiter with a still clock advanced only by the poll sleeper: zero real delay.
            MutableClock clock = new MutableClock();
            CentralPollSleeper sleeper = clock::advance;
            CentralPortalClient portalClient = new CentralPortalClient(NetworkTransport.direct());
            CentralDeploymentWaiter waiter =
                    new CentralDeploymentWaiter(portalClient, clock, sleeper, Duration.ofSeconds(5));
            WorkspaceCentralPublisher publisher = new WorkspaceCentralPublisher(
                    portalClient, waiter, name -> "ZOLT_PORTAL_TOKEN".equals(name) ? "portal-secret" : null);

            WorkspacePublishReport report = publisher.publish(
                    workspace,
                    family,
                    new WorkspacePublishService.Options(false, true, false, Optional.of(Duration.ofSeconds(60))));

            assertTrue(report.ok(), () -> "blockers: " + report.blockers());
            assertTrue(report.uploaded());
            assertEquals(Optional.of("dep-42"), report.deploymentId());
            assertEquals(3, statusCalls.get(), "polled PENDING -> VALIDATING -> PUBLISHED");
        } finally {
            server.stop(0);
        }
    }

    private static List<MemberPublication> family(Path root, String baseUrl, PublishSigningSettings signing)
            throws IOException {
        PublishCentralSettings central = new PublishCentralSettings(
                true, Optional.of("ZOLT_PORTAL_TOKEN"), CentralPublishingType.AUTOMATIC, Optional.empty(), baseUrl);
        PublishSettings publish = new PublishSettings("", "", List.of("main"), Map.of(), signing, central);

        List<MemberPublication> members = new ArrayList<>();
        members.add(jarMember(root, "acme-core", "com.acme", publish));
        members.add(jarMember(root, "acme-http", "com.acme", publish));
        members.add(bomMember(root, "acme-bom", "com.acme.platform", publish));
        return members;
    }

    private static MemberPublication jarMember(Path root, String name, String group, PublishSettings publish)
            throws IOException {
        Path memberRoot = root.resolve(name);
        Files.createDirectories(memberRoot.resolve("target/publish"));
        Files.writeString(memberRoot.resolve("target/" + name + "-1.0.0.jar"), name + "-jar");
        Files.writeString(memberRoot.resolve("target/publish/" + name + "-1.0.0-sources.jar"), name + "-sources");
        Files.writeString(memberRoot.resolve("target/publish/" + name + "-1.0.0-javadoc.jar"), name + "-javadoc");
        Files.writeString(memberRoot.resolve("target/publish/" + name + "-1.0.0-cyclonedx.json"), "{}");
        Files.writeString(memberRoot.resolve("target/publish/" + name + "-1.0.0.pom"), "<project/>");
        String base = group.replace('.', '/') + "/" + name + "/1.0.0/" + name + "-1.0.0";
        List<PublishArtifactPlan> supplementals = List.of(
                supplemental(name, group, "sources", "jar"),
                supplemental(name, group, "javadoc", "jar"),
                supplemental(name, group, "cyclonedx", "json"));
        PublishDryRunPlan plan = new PublishDryRunPlan(
                group + ":" + name + ":1.0.0",
                "release",
                "central",
                "https://central.sonatype.com",
                "main",
                Path.of("target/" + name + "-1.0.0.jar"),
                "sha256:jar",
                base + ".jar",
                supplementals,
                Path.of("target/publish/" + name + "-1.0.0.pom"),
                Path.of("target/publish/" + name + "-1.0.0.pom"),
                "sha256:pom",
                base + ".pom",
                List.of(),
                "",
                List.of(),
                false);
        return new MemberPublication(memberRoot, name, group + ":" + name + ":1.0.0", false, plan, publish, Map.of());
    }

    private static MemberPublication bomMember(Path root, String name, String group, PublishSettings publish)
            throws IOException {
        Path memberRoot = root.resolve(name);
        Files.createDirectories(memberRoot.resolve("target/publish"));
        Files.writeString(
                memberRoot.resolve("target/publish/" + name + "-1.0.0.pom"),
                "<project><packaging>pom</packaging></project>");
        String base = group.replace('.', '/') + "/" + name + "/1.0.0/" + name + "-1.0.0";
        PublishDryRunPlan plan = new PublishDryRunPlan(
                group + ":" + name + ":1.0.0",
                "release",
                "central",
                "https://central.sonatype.com",
                "bom",
                Path.of("target/publish/" + name + "-1.0.0.pom"),
                "sha256:pom",
                "",
                List.of(),
                Path.of("target/publish/" + name + "-1.0.0.pom"),
                Path.of("target/publish/" + name + "-1.0.0.pom"),
                "sha256:pom",
                base + ".pom",
                List.of(),
                "",
                List.of(),
                true);
        return new MemberPublication(memberRoot, name, group + ":" + name + ":1.0.0", true, plan, publish, Map.of());
    }

    private static PublishArtifactPlan supplemental(String name, String group, String classifier, String extension) {
        String base = group.replace('.', '/') + "/" + name + "/1.0.0/" + name + "-1.0.0-" + classifier + "." + extension;
        return new PublishArtifactPlan(
                classifier,
                Optional.of(classifier),
                Path.of("target/publish/" + name + "-1.0.0-" + classifier + "." + extension),
                "sha256:" + classifier,
                base);
    }

    private static Workspace workspace(Path root) {
        return new Workspace(
                root,
                root.resolve("zolt.toml"),
                new WorkspaceConfig("family", List.of(), List.of(), Map.of(), Map.of()),
                List.of());
    }

    private static Set<String> bundleEntries(Path bundle) throws IOException {
        Set<String> names = new java.util.HashSet<>();
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(bundle))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                names.add(entry.getName());
            }
        }
        return names;
    }

    private static String statusJson(String state) {
        return "{\"deploymentId\":\"dep\",\"deploymentState\":\"" + state + "\"}";
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, int status, String body)
            throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static HttpServer startServer() {
        try {
            return HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException exception) {
            assumeTrue(false, "local HTTP server sockets are unavailable: " + exception.getMessage());
            throw new IllegalStateException(exception);
        }
    }

    private static final class MutableClock extends Clock {
        private Instant instant = Instant.parse("2026-01-01T00:00:00Z");

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }
    }
}
