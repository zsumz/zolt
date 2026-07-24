package sh.zolt.workspace.publish;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import sh.zolt.project.RepositoryCredentialSettings;
import sh.zolt.publish.PublishArtifactPlan;
import sh.zolt.publish.PublishDryRunPlan;
import sh.zolt.publish.PublishRepositorySettings;
import sh.zolt.publish.PublishSettings;
import sh.zolt.publish.PublishSigningSettings;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class WorkspaceRepositoryUploaderTest {
    private static final WorkspacePublishService.Options OPTIONS =
            new WorkspacePublishService.Options(false, false, false, false, Optional.empty());
    private final WorkspaceRepositoryUploader uploader = new WorkspaceRepositoryUploader();

    @Test
    void writesArtifactSupplementalsPomAndChecksumsToAFileRepository(@TempDir Path tempDir) throws IOException {
        Path memberRoot = tempDir.resolve("acme-core");
        writeFile(memberRoot.resolve("target/acme-core-1.0.0.jar"), "jar-bytes");
        writeFile(memberRoot.resolve("target/publish/acme-core-1.0.0-sources.jar"), "sources-bytes");
        writeFile(memberRoot.resolve("target/publish/acme-core-1.0.0.pom"), "<project/>");
        Path repository = tempDir.resolve("repo");

        PublishArtifactPlan sources = new PublishArtifactPlan(
                "sources",
                Optional.of("sources"),
                Path.of("target/publish/acme-core-1.0.0-sources.jar"),
                "sha256:sources",
                "com/acme/acme-core/1.0.0/acme-core-1.0.0-sources.jar");
        PublishDryRunPlan plan = jarPlan("com.acme:acme-core:1.0.0", repository.toUri().toString(), List.of(sources));

        WorkspacePublishReport report =
                stageThenUpload(List.of(fileMember(memberRoot, "acme-core", plan)), OPTIONS, tempDir.resolve("staging"));

        assertTrue(report.ok(), () -> "blockers: " + report.blockers());
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
    void pomOnlyMemberWritesOnlyThePom(@TempDir Path tempDir) {
        Path memberRoot = tempDir.resolve("acme-bom");
        writeFile(
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

        WorkspacePublishReport report =
                stageThenUpload(List.of(fileMember(memberRoot, "acme-bom", plan)), OPTIONS, tempDir.resolve("staging"));

        assertTrue(report.ok());
        Path base = repository.resolve("com/acme/platform/acme-bom/1.0.0");
        assertTrue(Files.exists(base.resolve("acme-bom-1.0.0.pom")));
        assertFalse(Files.exists(base.resolve("acme-bom-1.0.0.jar")));
    }

    @Test
    void authenticatesEveryRequestToACredentialedHttpRepository(@TempDir Path tempDir) {
        PublishFixtureRepository server = PublishFixtureRepository.start();
        try {
            String base = server.baseUri();
            Path core = tempDir.resolve("acme-core");
            writeFile(core.resolve("target/acme-core-1.0.0.jar"), "core-jar");
            writeFile(core.resolve("target/publish/acme-core-1.0.0-sources.jar"), "core-sources");
            writeFile(core.resolve("target/publish/acme-core-1.0.0-javadoc.jar"), "core-javadoc");
            writeFile(core.resolve("target/publish/acme-core-1.0.0-cyclonedx.json"), "{}");
            writeFile(core.resolve("target/publish/acme-core-1.0.0.pom"), "<project/>");

            PublishArtifactPlan sources = supplemental("acme-core", "sources", "jar");
            PublishArtifactPlan javadoc = supplemental("acme-core", "javadoc", "jar");
            PublishArtifactPlan sbom = supplemental("acme-core", "cyclonedx", "json");
            PublishRepositorySettings repo = new PublishRepositorySettings("nexus", base, Optional.of("nexus-creds"));
            PublishSettings publish = new PublishSettings("nexus", "", List.of("main"), Map.of("nexus", repo));
            Map<String, RepositoryCredentialSettings> credentials =
                    Map.of("nexus-creds", RepositoryCredentialSettings.token("nexus-creds", "ZOLT_NEXUS_TOKEN"));
            Function<String, String> environment =
                    name -> "ZOLT_NEXUS_TOKEN".equals(name) ? "portal-token-value" : null;

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

            WorkspacePublishReport report = stageThenUpload(
                    new WorkspacePublishStaging(environment), List.of(member), OPTIONS, tempDir.resolve("staging"));

            assertTrue(report.ok(), () -> "blockers: " + report.blockers());
            assertTrue(report.uploaded());
            // Every request — the idempotency probe (GET) and the upload (PUT) — authenticates identically.
            assertFalse(server.authByPath.isEmpty());
            for (Map.Entry<String, String> entry : server.authByPath.entrySet()) {
                assertEquals("Bearer portal-token-value", entry.getValue(), "unauthenticated request: " + entry.getKey());
            }
            assertTrue(server.has("/maven2/com/acme/acme-core/1.0.0/acme-core-1.0.0.jar"));
            assertTrue(server.has("/maven2/com/acme/acme-core/1.0.0/acme-core-1.0.0-sources.jar"));
            assertTrue(server.has("/maven2/com/acme/acme-core/1.0.0/acme-core-1.0.0-javadoc.jar"));
            assertTrue(server.has("/maven2/com/acme/acme-core/1.0.0/acme-core-1.0.0-cyclonedx.json"));
            assertTrue(server.has("/maven2/com/acme/acme-core/1.0.0/acme-core-1.0.0.pom"));
            assertTrue(server.has("/maven2/com/acme/acme-core/1.0.0/acme-core-1.0.0.jar.sha256"));
        } finally {
            server.close();
        }
    }

    @Test
    void signsEveryUploadedFileWhenSigningIsEnabled(@TempDir Path tempDir) throws Exception {
        assumeTrue(SigningTestSupport.gpgAvailable(), "gpg is not installed");
        Path gnupgHome = SigningTestSupport.isolatedGnupgHome(tempDir);
        SigningTestSupport.generateSigningKey(gnupgHome);

        Path memberRoot = tempDir.resolve("acme-core");
        writeFile(memberRoot.resolve("target/acme-core-1.0.0.jar"), "jar-bytes");
        writeFile(memberRoot.resolve("target/publish/acme-core-1.0.0-sources.jar"), "sources-bytes");
        writeFile(memberRoot.resolve("target/publish/acme-core-1.0.0.pom"), "<project/>");
        Path repository = tempDir.resolve("repo");

        PublishArtifactPlan sources = new PublishArtifactPlan(
                "sources",
                Optional.of("sources"),
                Path.of("target/publish/acme-core-1.0.0-sources.jar"),
                "sha256:sources",
                "com/acme/acme-core/1.0.0/acme-core-1.0.0-sources.jar");
        PublishDryRunPlan plan = jarPlan("com.acme:acme-core:1.0.0", repository.toUri().toString(), List.of(sources));
        PublishSigningSettings signing =
                new PublishSigningSettings(true, Optional.empty(), Optional.of("ZOLT_TEST_GPG_PASS"));
        PublishSettings publish = new PublishSettings("local", "", List.of("main"), Map.of(), signing);
        MemberPublication member = new MemberPublication(
                memberRoot, "acme-core", "com.acme:acme-core:1.0.0", false, plan, publish, Map.of());

        WorkspacePublishReport report = stageThenUpload(
                new WorkspacePublishStaging(SigningTestSupport.signingEnvironment(gnupgHome)),
                List.of(member),
                OPTIONS,
                tempDir.resolve("staging"));

        assertTrue(report.ok(), () -> "blockers: " + report.blockers());
        Path base = repository.resolve("com/acme/acme-core/1.0.0");
        // Every artifact, supplemental, and the POM gets a detached signature and its own checksums.
        assertTrue(Files.exists(base.resolve("acme-core-1.0.0.jar.asc")), "jar signature");
        assertTrue(Files.exists(base.resolve("acme-core-1.0.0-sources.jar.asc")), "sources signature");
        assertTrue(Files.exists(base.resolve("acme-core-1.0.0.pom.asc")), "pom signature");
        assertTrue(Files.exists(base.resolve("acme-core-1.0.0.jar.asc.sha256")), "signature checksum");
        assertTrue(Files.exists(base.resolve("acme-core-1.0.0.jar.sha256")));
    }

    @Test
    void failedMemberEmitsAnExactResumeCommandPreservingSemanticOptions(@TempDir Path tempDir) {
        // The repository stores the provider's uploads but rejects the consumer's jar PUT (403), so
        // publishing fails after the provider fully landed.
        PublishFixtureRepository server = PublishFixtureRepository.start();
        server.failPutPathSuffix = "/acme-http/1.0.0/acme-http-1.0.0.jar";
        try {
            String base = server.baseUri();
            MemberPublication core = httpJarMember(tempDir, "acme-core", base);
            MemberPublication http = httpJarMember(tempDir, "acme-http", base);
            WorkspacePublishService.Options mixedSbom =
                    new WorkspacePublishService.Options(false, false, true, true, Optional.empty());

            WorkspacePublishReport report =
                    stageThenUpload(List.of(core, http), mixedSbom, tempDir.resolve("staging"));

            assertFalse(report.ok());
            assertTrue(report.blockers().get(0).contains("com.acme:acme-http"), report.blockers()::toString);
            assertTrue(server.has("/maven2/com/acme/acme-core/1.0.0/acme-core-1.0.0.jar"), "provider uploaded");
            assertFalse(server.has("/maven2/com/acme/acme-http/1.0.0/acme-http-1.0.0.jar"), "consumer rejected");
            assertEquals(
                    Optional.of("zolt publish --workspace --resume-members acme-http --allow-mixed-versions --sbom"),
                    report.resumeCommand());
        } finally {
            server.close();
        }
    }

    @Test
    void resumeReUploadsOnlyTheFilesThatDidNotLandAndSkipsThoseThatDid(@TempDir Path tempDir) {
        // A mid-member failure: the jar and its md5 land, then the sha1 checksum PUT is rejected once.
        PublishFixtureRepository server = PublishFixtureRepository.start();
        String jar = "/maven2/com/acme/acme-core/1.0.0/acme-core-1.0.0.jar";
        server.failPutPathSuffix = "/acme-core-1.0.0.jar.sha1";
        try {
            MemberPublication core = httpJarMember(tempDir, "acme-core", server.baseUri());
            WorkspacePublishStaging.Preparation prep =
                    new WorkspacePublishStaging().materialize(List.of(core), tempDir.resolve("staging"), OPTIONS);
            assertTrue(prep.blockers().isEmpty(), () -> "blockers: " + prep.blockers());

            WorkspacePublishReport first = uploader.upload(prep.members(), OPTIONS);
            assertFalse(first.ok(), "the sha1 checksum PUT was rejected");
            assertTrue(server.has(jar), "jar landed before the failure");
            assertTrue(server.has(jar + ".md5"), "the md5 landed before the failure");
            assertFalse(server.has(jar + ".sha1"), "the sha1 did not land");

            // Resume: the same immutable repository (409 on re-PUT) now accepts the sha1.
            server.failPutPathSuffix = null;
            WorkspacePublishReport second = uploader.upload(prep.members(), OPTIONS);

            assertTrue(second.ok(), () -> "resume blockers: " + second.blockers());
            // Already-landed files were skipped (idempotency): each was PUT exactly once, never re-PUT.
            assertEquals(1, server.putCount(jar), "jar must not be re-PUT on resume");
            assertEquals(1, server.putCount(jar + ".md5"), "md5 must not be re-PUT on resume");
            assertTrue(server.has(jar + ".sha1"), "the previously-failed sha1 uploaded on resume");
            assertTrue(server.has("/maven2/com/acme/acme-core/1.0.0/acme-core-1.0.0.pom"), "pom uploaded on resume");
        } finally {
            server.close();
        }
    }

    @Test
    void anExistingReleasePathWithDifferentContentIsAHardFailureWithNoResume(@TempDir Path tempDir) {
        PublishFixtureRepository server = PublishFixtureRepository.start();
        // The jar path is already occupied by DIFFERENT bytes than this publish would upload.
        server.store.put("/maven2/com/acme/acme-core/1.0.0/acme-core-1.0.0.jar", "someone-elses-jar".getBytes(StandardCharsets.UTF_8));
        try {
            MemberPublication core = httpJarMember(tempDir, "acme-core", server.baseUri());

            WorkspacePublishReport report = stageThenUpload(List.of(core), OPTIONS, tempDir.resolve("staging"));

            assertFalse(report.ok());
            assertTrue(report.blockers().get(0).contains("already holds different content"), report.blockers()::toString);
            assertFalse(report.resumeCommand().isPresent(), "a content conflict is not resumable");
        } finally {
            server.close();
        }
    }

    private WorkspacePublishReport stageThenUpload(
            List<MemberPublication> members, WorkspacePublishService.Options options, Path stagingRoot) {
        return stageThenUpload(new WorkspacePublishStaging(), members, options, stagingRoot);
    }

    private WorkspacePublishReport stageThenUpload(
            WorkspacePublishStaging staging,
            List<MemberPublication> members,
            WorkspacePublishService.Options options,
            Path stagingRoot) {
        WorkspacePublishStaging.Preparation preparation = staging.materialize(members, stagingRoot, options);
        if (!preparation.blockers().isEmpty()) {
            List<WorkspacePublishReport.Member> reportMembers = new ArrayList<>();
            for (MemberPublication member : members) {
                reportMembers.add(member.toReportMember());
            }
            return new WorkspacePublishReport(
                    reportMembers, preparation.blockers(), false, Optional.empty(), Optional.empty());
        }
        return uploader.upload(preparation.members(), options);
    }

    private static MemberPublication httpJarMember(Path root, String name, String base) {
        Path memberRoot = root.resolve(name);
        writeFile(memberRoot.resolve("target/" + name + "-1.0.0.jar"), name + "-jar");
        writeFile(memberRoot.resolve("target/publish/" + name + "-1.0.0.pom"), "<project/>");
        PublishRepositorySettings repo = new PublishRepositorySettings("local", base, Optional.empty());
        PublishSettings publish = new PublishSettings("local", "", List.of("main"), Map.of("local", repo));
        return new MemberPublication(
                memberRoot,
                name,
                "com.acme:" + name + ":1.0.0",
                false,
                jarPlan("com.acme:" + name + ":1.0.0", base, List.of()),
                publish,
                Map.of());
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

    private static void writeFile(Path path, String content) {
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, content);
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
