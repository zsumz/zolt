package sh.zolt.workspace.publish;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.sun.net.httpserver.HttpServer;
import sh.zolt.maven.repository.MavenRepositoryClient;
import sh.zolt.project.RepositoryCredentialSettings;
import sh.zolt.publish.PublishArtifactPlan;
import sh.zolt.publish.PublishDryRunPlan;
import sh.zolt.publish.PublishRepositorySettings;
import sh.zolt.publish.PublishSettings;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class WorkspaceRepositoryUploaderTest {
    private final WorkspaceRepositoryUploader uploader = new WorkspaceRepositoryUploader();

    @Test
    void writesArtifactSupplementalsPomAndChecksumsToAFileRepository(@TempDir Path tempDir) throws IOException {
        Path memberRoot = tempDir.resolve("acme-core");
        Files.createDirectories(memberRoot.resolve("target/publish"));
        Files.writeString(memberRoot.resolve("target/acme-core-1.0.0.jar"), "jar-bytes");
        Files.writeString(memberRoot.resolve("target/publish/acme-core-1.0.0-sources.jar"), "sources-bytes");
        Files.writeString(memberRoot.resolve("target/publish/acme-core-1.0.0.pom"), "<project/>");
        Path repository = tempDir.resolve("repo");
        Files.createDirectories(repository);

        PublishArtifactPlan sources = new PublishArtifactPlan(
                "sources",
                Optional.of("sources"),
                Path.of("target/publish/acme-core-1.0.0-sources.jar"),
                "sha256:sources",
                "com/acme/acme-core/1.0.0/acme-core-1.0.0-sources.jar");
        PublishDryRunPlan plan = jarPlan(
                "com.acme:acme-core:1.0.0", repository.toUri().toString(), List.of(sources));

        WorkspacePublishReport report = uploader.upload(List.of(fileMember(memberRoot, "acme-core", plan)));

        assertTrue(report.ok());
        assertTrue(report.uploaded());
        Path base = repository.resolve("com/acme/acme-core/1.0.0");
        assertTrue(Files.exists(base.resolve("acme-core-1.0.0.jar")));
        assertTrue(Files.exists(base.resolve("acme-core-1.0.0-sources.jar")), "supplemental sources jar uploaded");
        assertTrue(Files.exists(base.resolve("acme-core-1.0.0-sources.jar.sha256")), "supplemental checksum uploaded");
        assertTrue(Files.exists(base.resolve("acme-core-1.0.0.pom")));
        assertTrue(Files.exists(base.resolve("acme-core-1.0.0.jar.sha1")));
        assertTrue(Files.exists(base.resolve("acme-core-1.0.0.pom.sha256")));
        assertEquals(40, Files.readString(base.resolve("acme-core-1.0.0.jar.sha1")).length());
        assertFalse(report.resumeCommand().isPresent());
    }

    @Test
    void pomOnlyMemberWritesOnlyThePom(@TempDir Path tempDir) throws IOException {
        Path memberRoot = tempDir.resolve("acme-bom");
        Files.createDirectories(memberRoot.resolve("target/publish"));
        Files.writeString(
                memberRoot.resolve("target/publish/acme-bom-1.0.0.pom"),
                "<project><packaging>pom</packaging></project>");
        Path repository = tempDir.resolve("repo");

        PublishDryRunPlan plan = new PublishDryRunPlan(
                "com.acme.platform:acme-bom:1.0.0",
                "release",
                "local",
                repository.toUri().toString(),
                "bom",
                Path.of("target/publish/acme-bom-1.0.0.pom"),
                "sha256:pom",
                "",
                List.of(),
                Path.of("target/publish/acme-bom-1.0.0.pom"),
                Path.of("target/publish/acme-bom-1.0.0.pom"),
                "sha256:pom",
                "com/acme/platform/acme-bom/1.0.0/acme-bom-1.0.0.pom",
                List.of(),
                "",
                List.of(),
                true);

        WorkspacePublishReport report = uploader.upload(List.of(fileMember(memberRoot, "acme-bom", plan)));

        assertTrue(report.ok());
        Path base = repository.resolve("com/acme/platform/acme-bom/1.0.0");
        assertTrue(Files.exists(base.resolve("acme-bom-1.0.0.pom")));
        assertFalse(Files.exists(base.resolve("acme-bom-1.0.0.jar")));
    }

    @Test
    void authenticatesEveryRequestToACredentialedHttpRepository(@TempDir Path tempDir) throws IOException {
        HttpServer server = startServer();
        Map<String, String> authByPath = new ConcurrentHashMap<>();
        Set<String> puts = ConcurrentHashMap.newKeySet();
        server.createContext("/maven2/", exchange -> {
            String header = exchange.getRequestHeaders().getFirst("Authorization");
            authByPath.put(exchange.getRequestURI().getPath(), header == null ? "<none>" : header);
            puts.add(exchange.getRequestURI().getPath());
            exchange.getRequestBody().readAllBytes();
            exchange.sendResponseHeaders(201, -1);
            exchange.close();
        });
        server.start();
        try {
            String base = "http://127.0.0.1:" + server.getAddress().getPort() + "/maven2/";
            Path core = tempDir.resolve("acme-core");
            Files.createDirectories(core.resolve("target/publish"));
            Files.writeString(core.resolve("target/acme-core-1.0.0.jar"), "core-jar");
            Files.writeString(core.resolve("target/publish/acme-core-1.0.0-sources.jar"), "core-sources");
            Files.writeString(core.resolve("target/publish/acme-core-1.0.0-javadoc.jar"), "core-javadoc");
            Files.writeString(core.resolve("target/publish/acme-core-1.0.0-cyclonedx.json"), "{}");
            Files.writeString(core.resolve("target/publish/acme-core-1.0.0.pom"), "<project/>");

            PublishArtifactPlan sources = supplemental("acme-core", "sources", "jar");
            PublishArtifactPlan javadoc = supplemental("acme-core", "javadoc", "jar");
            PublishArtifactPlan sbom = supplemental("acme-core", "cyclonedx", "json");
            PublishRepositorySettings repo = new PublishRepositorySettings("nexus", base, Optional.of("nexus-creds"));
            PublishSettings publish = new PublishSettings("nexus", "", List.of("main"), Map.of("nexus", repo));
            Map<String, RepositoryCredentialSettings> credentials =
                    Map.of("nexus-creds", RepositoryCredentialSettings.token("nexus-creds", "ZOLT_NEXUS_TOKEN"));
            Function<String, String> environment =
                    name -> "ZOLT_NEXUS_TOKEN".equals(name) ? "portal-token-value" : null;
            WorkspaceRepositoryUploader authenticated =
                    new WorkspaceRepositoryUploader(new MavenRepositoryClient(), environment);

            PublishDryRunPlan plan = new PublishDryRunPlan(
                    "com.acme:acme-core:1.0.0",
                    "release",
                    "nexus",
                    base,
                    "main",
                    Path.of("target/acme-core-1.0.0.jar"),
                    "sha256:jar",
                    "com/acme/acme-core/1.0.0/acme-core-1.0.0.jar",
                    List.of(sources, javadoc, sbom),
                    Path.of("target/publish/acme-core-1.0.0.pom"),
                    Path.of("target/publish/acme-core-1.0.0.pom"),
                    "sha256:pom",
                    "com/acme/acme-core/1.0.0/acme-core-1.0.0.pom",
                    List.of(),
                    "",
                    List.of(),
                    false);
            MemberPublication member = new MemberPublication(
                    core, "acme-core", "com.acme:acme-core:1.0.0", false, plan, publish, credentials);

            WorkspacePublishReport report = authenticated.upload(List.of(member));

            assertTrue(report.ok(), () -> "blockers: " + report.blockers());
            assertTrue(report.uploaded());
            // Every artifact, supplemental, POM, and their checksum sidecars authenticate identically.
            assertFalse(authByPath.isEmpty());
            for (Map.Entry<String, String> entry : authByPath.entrySet()) {
                assertEquals("Bearer portal-token-value", entry.getValue(), "unauthenticated request: " + entry.getKey());
            }
            assertTrue(puts.contains("/maven2/com/acme/acme-core/1.0.0/acme-core-1.0.0.jar"));
            assertTrue(puts.contains("/maven2/com/acme/acme-core/1.0.0/acme-core-1.0.0-sources.jar"));
            assertTrue(puts.contains("/maven2/com/acme/acme-core/1.0.0/acme-core-1.0.0-javadoc.jar"));
            assertTrue(puts.contains("/maven2/com/acme/acme-core/1.0.0/acme-core-1.0.0-cyclonedx.json"));
            assertTrue(puts.contains("/maven2/com/acme/acme-core/1.0.0/acme-core-1.0.0.pom"));
            assertTrue(puts.contains("/maven2/com/acme/acme-core/1.0.0/acme-core-1.0.0.jar.sha256"));
        } finally {
            server.stop(0);
        }
    }

    private static PublishDryRunPlan jarPlan(String coord, String repoUrl, List<PublishArtifactPlan> supplementals) {
        String[] parts = coord.split(":");
        String group = parts[0].replace('.', '/');
        String name = parts[1];
        String version = parts[2];
        String base = group + "/" + name + "/" + version + "/" + name + "-" + version;
        return new PublishDryRunPlan(
                coord,
                "release",
                "local",
                repoUrl,
                "main",
                Path.of("target/" + name + "-" + version + ".jar"),
                "sha256:jar",
                base + ".jar",
                supplementals,
                Path.of("target/publish/" + name + "-" + version + ".pom"),
                Path.of("target/publish/" + name + "-" + version + ".pom"),
                "sha256:pom",
                base + ".pom",
                List.of(),
                "",
                List.of(),
                false);
    }

    private static PublishArtifactPlan supplemental(String name, String classifier, String extension) {
        return new PublishArtifactPlan(
                classifier,
                Optional.of(classifier),
                Path.of("target/publish/" + name + "-1.0.0-" + classifier + "." + extension),
                "sha256:" + classifier,
                "com/acme/" + name + "/1.0.0/" + name + "-1.0.0-" + classifier + "." + extension);
    }

    private static MemberPublication fileMember(Path memberRoot, String name, PublishDryRunPlan plan) {
        return new MemberPublication(
                memberRoot,
                name,
                plan.coordinate(),
                plan.pomOnly(),
                plan,
                new PublishSettings("local", "", List.of("main"), Map.of()),
                Map.of());
    }

    private static HttpServer startServer() {
        try {
            return HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException exception) {
            assumeTrue(false, "local HTTP server sockets are unavailable: " + exception.getMessage());
            throw new IllegalStateException(exception);
        }
    }
}
