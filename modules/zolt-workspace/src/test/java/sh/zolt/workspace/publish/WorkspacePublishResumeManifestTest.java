package sh.zolt.workspace.publish;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import sh.zolt.publish.PublishArtifactPlan;
import sh.zolt.publish.PublishDryRunPlan;
import sh.zolt.publish.PublishRepositorySettings;
import sh.zolt.publish.PublishSettings;
import sh.zolt.publish.PublishSigningSettings;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The transaction-manifest guarantees for a resumed plain-repository publish: an already-uploaded
 * detached signature is reused rather than re-signed to a diverging one, a changed repository or
 * signing setup is refused, an already-landed path is verified (and re-uploaded if it was deleted)
 * rather than skipped blindly, a legacy v1 manifest is refused, and a multi-MB artifact streams
 * through staging and upload without being buffered whole.
 */
final class WorkspacePublishResumeManifestTest {
    private static final WorkspacePublishService.Options OPTIONS =
            new WorkspacePublishService.Options(false, false, false, false, Optional.empty());

    @Test
    void resumeAfterASignatureChecksumFailureReusesTheSignatureInsteadOfReSigning(@TempDir Path tempDir)
            throws Exception {
        assumeTrue(SigningTestSupport.gpgAvailable(), "gpg is not installed");
        Path gnupgHome = SigningTestSupport.isolatedGnupgHome(tempDir);
        SigningTestSupport.generateSigningKey(gnupgHome);
        WorkspacePublishStaging staging = new WorkspacePublishStaging(SigningTestSupport.signingEnvironment(gnupgHome));

        PublishFixtureRepository server = PublishFixtureRepository.start();
        try {
            MemberPublication member = signedJarMember(tempDir, "acme-core", server.baseUri());
            Path stagingRoot = tempDir.resolve("staging");
            Path statePath = tempDir.resolve("resume-state");
            String ascPath = "/maven2/com/acme/acme-core/1.0.0/acme-core-1.0.0.jar.asc";
            String ascChecksum = ascPath + ".sha256";

            // Attempt 1: the jar's detached signature (S1) lands, then its .asc.sha256 PUT is rejected.
            server.failPutPathSuffix = "/acme-core-1.0.0.jar.asc.sha256";
            WorkspacePublishStaging.Preparation prep1 = staging.materialize(List.of(member), stagingRoot, OPTIONS);
            assertTrue(prep1.blockers().isEmpty(), () -> "stage blockers: " + prep1.blockers());
            WorkspacePublishReport first = new WorkspaceRepositoryUploader()
                    .upload(prep1.members(), OPTIONS, Set.of(), statePath);
            assertFalse(first.ok());
            assertTrue(server.has(ascPath), "the signature (S1) landed before the failure");
            assertFalse(server.has(ascChecksum), "its checksum did not land");
            byte[] signatureS1 = server.store.get(ascPath);

            // Resume: re-stage against the manifest (reusing S1), upload only the missing checksum.
            ResumeState.ReadOutcome outcome = ResumeState.read(statePath);
            assertTrue(outcome.present(), "a v3 transaction manifest was written on failure");
            server.failPutPathSuffix = null;
            WorkspacePublishStaging.Preparation prep2 =
                    staging.materialize(List.of(member), stagingRoot, OPTIONS, outcome.state());
            assertTrue(prep2.blockers().isEmpty(), () -> "resume-stage blockers: " + prep2.blockers());
            WorkspacePublishReport second = new WorkspaceRepositoryUploader()
                    .upload(prep2.members(), OPTIONS, outcome.state().orElseThrow().completed(), statePath);

            assertTrue(second.ok(), () -> "resume blockers: " + second.blockers());
            assertEquals(1, server.putCount(ascPath), "the signature must not be re-signed and re-PUT on resume");
            assertArrayEquals(signatureS1, server.store.get(ascPath), "the remote signature is still S1");
            // The uploaded checksum matches the REMOTE signature bytes — proof no S1/S2 divergence occurred.
            assertEquals(
                    sha256Hex(signatureS1),
                    new String(server.store.get(ascChecksum), StandardCharsets.UTF_8).trim(),
                    "the uploaded .asc.sha256 is the digest of the remote signature");
            assertFalse(Files.exists(statePath), "the manifest is cleared after a successful resume");
        } finally {
            server.close();
        }
    }

    @Test
    void resumeAgainstAChangedRepositoryTargetRefusesNamingBothDestinations(@TempDir Path tempDir) {
        StagedArtifact jar = jarArtifact();
        RepositoryTarget original = RepositoryTarget.remote(URI.create("https://repo-a.example/releases"), Optional.empty());
        RepositoryTarget moved = RepositoryTarget.remote(URI.create("https://repo-b.example/releases"), Optional.empty());
        ResumeState manifest = ResumeState.of(
                List.of(stagedMember(original, "unsigned", jar)), OPTIONS, List.of("acme-core"), Set.of());

        List<String> blockers =
                manifest.validate(List.of(stagedMember(moved, "unsigned", jar)), OPTIONS, List.of("acme-core"));

        assertEquals(1, blockers.size(), blockers::toString);
        assertTrue(blockers.get(0).contains("https://repo-a.example/releases"), blockers::toString);
        assertTrue(blockers.get(0).contains("https://repo-b.example/releases"), blockers::toString);
        assertTrue(blockers.get(0).contains("different publish repository"), blockers::toString);
    }

    @Test
    void resumeAgainstAChangedSigningKeyRefuses(@TempDir Path tempDir) {
        StagedArtifact jar = jarArtifact();
        RepositoryTarget target = RepositoryTarget.remote(URI.create("https://repo.example/releases"), Optional.empty());
        ResumeState manifest = ResumeState.of(
                List.of(stagedMember(target, "key=AAAA1111;sde=none", jar)), OPTIONS, List.of("acme-core"), Set.of());

        List<String> blockers = manifest.validate(
                List.of(stagedMember(target, "key=BBBB2222;sde=none", jar)), OPTIONS, List.of("acme-core"));

        assertEquals(1, blockers.size(), blockers::toString);
        assertTrue(blockers.get(0).contains("signing configuration"), blockers::toString);
        assertTrue(blockers.get(0).contains("AAAA1111") && blockers.get(0).contains("BBBB2222"), blockers::toString);
    }

    @Test
    void resumeReUploadsACompletedPathThatWasDeletedRemotelyAndSkipsTheRest(@TempDir Path tempDir) throws IOException {
        PublishFixtureRepository server = PublishFixtureRepository.start();
        try {
            MemberPublication member = httpJarMember(tempDir, "acme-core", server.baseUri());
            Path stagingRoot = tempDir.resolve("staging");
            Path statePath = tempDir.resolve("resume-state");
            String jar = "/maven2/com/acme/acme-core/1.0.0/acme-core-1.0.0.jar";
            String jarSha256 = jar + ".sha256";

            // Attempt 1: the jar and its checksums land, then the POM PUT is rejected.
            server.failPutPathSuffix = "/acme-core-1.0.0.pom";
            WorkspacePublishStaging.Preparation prep1 =
                    new WorkspacePublishStaging().materialize(List.of(member), stagingRoot, OPTIONS);
            WorkspacePublishReport first = new WorkspaceRepositoryUploader()
                    .upload(prep1.members(), OPTIONS, Set.of(), statePath);
            assertFalse(first.ok());
            assertTrue(server.has(jarSha256), "the jar's sha256 sidecar landed before the failure");

            // A completed path is deleted from the repository before the resume.
            server.store.remove(jarSha256);
            server.failPutPathSuffix = null;
            ResumeState.ReadOutcome outcome = ResumeState.read(statePath);
            WorkspacePublishStaging.Preparation prep2 =
                    new WorkspacePublishStaging().materialize(List.of(member), stagingRoot, OPTIONS, outcome.state());
            WorkspacePublishReport second = new WorkspaceRepositoryUploader()
                    .upload(prep2.members(), OPTIONS, outcome.state().orElseThrow().completed(), statePath);

            assertTrue(second.ok(), () -> "resume blockers: " + second.blockers());
            assertEquals(2, server.putCount(jarSha256), "the deleted completed path is re-uploaded on resume");
            assertEquals(1, server.putCount(jar), "the jar itself is verified present (via a fetch) and not re-PUT");
            assertEquals(1, server.putCount(jar + ".md5"), "an intact completed path is not re-PUT");
            assertEquals(1, server.putCount(jar + ".sha1"), "an intact completed path is not re-PUT");
            assertFalse(Files.exists(statePath), "the manifest is cleared after a successful resume");
        } finally {
            server.close();
        }
    }

    @Test
    void resumeReuploadsMissingPrimaryEvenWhenItsSha256SidecarSurvives(@TempDir Path tempDir) throws IOException {
        PublishFixtureRepository server = PublishFixtureRepository.start();
        try {
            MemberPublication member = httpJarMember(tempDir, "acme-core", server.baseUri());
            Path stagingRoot = tempDir.resolve("staging");
            Path statePath = tempDir.resolve("resume-state");
            String jar = "/maven2/com/acme/acme-core/1.0.0/acme-core-1.0.0.jar";
            String jarSha256 = jar + ".sha256";

            server.failPutPathSuffix = "/acme-core-1.0.0.pom";
            WorkspacePublishStaging.Preparation prep1 =
                    new WorkspacePublishStaging().materialize(List.of(member), stagingRoot, OPTIONS);
            WorkspacePublishReport first = new WorkspaceRepositoryUploader()
                    .upload(prep1.members(), OPTIONS, Set.of(), statePath);
            assertFalse(first.ok());
            assertTrue(server.has(jarSha256));

            server.store.remove(jar);
            server.failPutPathSuffix = null;
            ResumeState state = ResumeState.read(statePath).state().orElseThrow();
            WorkspacePublishStaging.Preparation prep2 =
                    new WorkspacePublishStaging().materialize(List.of(member), stagingRoot, OPTIONS, Optional.of(state));
            WorkspacePublishReport second =
                    new WorkspaceRepositoryUploader().upload(prep2.members(), OPTIONS, state.completed(), statePath);

            assertTrue(second.ok(), () -> "resume blockers: " + second.blockers());
            assertEquals(2, server.putCount(jar), "the absent primary must be PUT again");
            assertEquals(1, server.putCount(jarSha256), "the surviving checksum remains untouched");
        } finally {
            server.close();
        }
    }

    @Test
    void resumeReverifiesAndRestoresAnOmittedProviderJar(@TempDir Path tempDir) throws IOException {
        resumeRestoresOmittedProviderFile(tempDir, ".jar");
    }

    @Test
    void resumeReverifiesAndRestoresAnOmittedProviderPom(@TempDir Path tempDir) throws IOException {
        resumeRestoresOmittedProviderFile(tempDir, ".pom");
    }

    @Test
    void providerOnlyTargetChangeIsRefusedEvenThoughTheResumeCommandOmitsIt() {
        RepositoryTarget original = RepositoryTarget.remote(
                URI.create("https://repo-a.example/releases"), Optional.empty());
        RepositoryTarget moved = RepositoryTarget.remote(
                URI.create("https://repo-b.example/releases"), Optional.empty());
        StagedMember provider = stagedMember(original, "unsigned", jarArtifact());
        StagedMember consumer = stagedMember(
                "acme-http",
                "com.acme:acme-http:1.0.0",
                original,
                "unsigned",
                new StagedArtifact(
                        "com/acme/acme-http/1.0.0/acme-http-1.0.0.jar",
                        Path.of("target/acme-http-1.0.0.jar"),
                        "def456"));
        ResumeState manifest =
                ResumeState.of(List.of(provider, consumer), List.of(consumer), OPTIONS, List.of("acme-http"), Set.of());
        StagedMember movedProvider = stagedMember(moved, "unsigned", jarArtifact());

        List<String> blockers =
                manifest.validate(List.of(movedProvider, consumer), OPTIONS, List.of("acme-http"));

        assertEquals(1, blockers.size(), blockers::toString);
        assertTrue(blockers.getFirst().contains("different publish repository"), blockers::toString);
        assertTrue(blockers.getFirst().contains("repo-a") && blockers.getFirst().contains("repo-b"));
    }

    @Test
    void providerProofRequiresItsCompleteRecordedPublicationSet() {
        RepositoryTarget target = RepositoryTarget.remote(
                URI.create("https://repo.example/releases"), Optional.empty());
        StagedArtifact jar = jarArtifact();
        StagedArtifact pom = new StagedArtifact(
                "com/acme/acme-core/1.0.0/acme-core-1.0.0.pom",
                Path.of("target/acme-core-1.0.0.pom"),
                "def456");
        ResumeState partial = ResumeState.of(
                List.of(stagedMember(target, "unsigned", jar, pom)),
                OPTIONS,
                List.of("acme-core"),
                Set.of(jar.repositoryPath()));

        assertFalse(partial.recordsPublished("com.acme:acme-core"));
    }

    @Test
    void aLegacyV1ManifestIsRefusedRatherThanGuessedAt(@TempDir Path tempDir) throws IOException {
        Path statePath = tempDir.resolve("resume-state");
        Files.writeString(statePath, "schema=zolt.publish-resume.v1\nplanHash=deadbeef\n"
                + "completed=com/acme/acme-core/1.0.0/acme-core-1.0.0.jar\n", StandardCharsets.UTF_8);

        ResumeState.ReadOutcome outcome = ResumeState.read(statePath);

        assertFalse(outcome.present(), "a v1 manifest yields no usable v3 state");
        assertTrue(outcome.legacy(), "a v1 manifest is recognised as legacy so the service can refuse actionably");
    }

    @Test
    void aV2ManifestIsAlsoRefusedBecauseItDidNotRecordCompletedProviders(@TempDir Path tempDir) throws IOException {
        Path statePath = tempDir.resolve("resume-state");
        Files.writeString(
                statePath,
                "schema=zolt.publish-resume.v2\nplanHash=deadbeef\nmembers=acme-http\n",
                StandardCharsets.UTF_8);

        ResumeState.ReadOutcome outcome = ResumeState.read(statePath);

        assertFalse(outcome.present());
        assertTrue(outcome.legacy());
    }

    @Test
    void aMultiMegabyteArtifactStreamsThroughStagingAndUploadWithoutBuffering(@TempDir Path tempDir) throws IOException {
        Path memberRoot = tempDir.resolve("acme-core");
        Path jar = memberRoot.resolve("target/acme-core-1.0.0.jar");
        byte[] large = new byte[5 * 1024 * 1024];
        new Random(20260724L).nextBytes(large);
        Files.createDirectories(jar.getParent());
        Files.write(jar, large);
        writeFile(memberRoot.resolve("target/publish/acme-core-1.0.0.pom"), "<project/>");
        Path repository = tempDir.resolve("repo");
        MemberPublication member = fileMember(memberRoot, "acme-core", repository.toUri().toString());

        WorkspacePublishStaging.Preparation prep =
                new WorkspacePublishStaging().materialize(List.of(member), tempDir.resolve("staging"), OPTIONS);
        assertTrue(prep.blockers().isEmpty(), () -> "stage blockers: " + prep.blockers());

        // The seam: the primary's staged digest is a streaming digest — correct for a 5MB input, no full buffer.
        StagedArtifact primary = prep.members().get(0).artifacts().get(0);
        assertEquals("com/acme/acme-core/1.0.0/acme-core-1.0.0.jar", primary.repositoryPath());
        assertEquals(sha256Hex(large), primary.stagedSha256(), "the primary's staged digest is computed over the stream");

        WorkspacePublishReport report = new WorkspaceRepositoryUploader().upload(prep.members(), OPTIONS);
        assertTrue(report.ok(), () -> "upload blockers: " + report.blockers());
        Path uploaded = repository.resolve("com/acme/acme-core/1.0.0/acme-core-1.0.0.jar");
        assertArrayEquals(large, Files.readAllBytes(uploaded), "the multi-MB artifact round-trips byte-for-byte");
        assertEquals(
                sha256Hex(large),
                Files.readString(repository.resolve("com/acme/acme-core/1.0.0/acme-core-1.0.0.jar.sha256")).trim(),
                "the uploaded sha256 sidecar is the streamed digest of the artifact");
    }

    private static StagedArtifact jarArtifact() {
        return new StagedArtifact(
                "com/acme/acme-core/1.0.0/acme-core-1.0.0.jar", Path.of("target/acme-core-1.0.0.jar"), "abc123");
    }

    private static StagedMember stagedMember(RepositoryTarget target, String signingIdentity, StagedArtifact artifact) {
        return stagedMember("acme-core", "com.acme:acme-core:1.0.0", target, signingIdentity, artifact);
    }

    private static StagedMember stagedMember(
            RepositoryTarget target, String signingIdentity, StagedArtifact... artifacts) {
        return stagedMember(
                "acme-core", "com.acme:acme-core:1.0.0", target, signingIdentity, artifacts);
    }

    private static StagedMember stagedMember(
            String path,
            String coordinate,
            RepositoryTarget target,
            String signingIdentity,
            StagedArtifact... artifacts) {
        WorkspacePublishReport.Member reportMember =
                new WorkspacePublishReport.Member(path, coordinate, false, null);
        return new StagedMember(reportMember, target, signingIdentity, List.of(artifacts));
    }

    private static void resumeRestoresOmittedProviderFile(Path tempDir, String extension) throws IOException {
        PublishFixtureRepository server = PublishFixtureRepository.start();
        try {
            MemberPublication provider = httpJarMember(tempDir, "acme-core", server.baseUri());
            MemberPublication consumer = httpJarMember(tempDir, "acme-http", server.baseUri());
            Path stagingRoot = tempDir.resolve("staging");
            Path statePath = tempDir.resolve("resume-state");
            server.failPutPathSuffix = "/acme-http-1.0.0.jar";

            WorkspacePublishStaging staging = new WorkspacePublishStaging();
            WorkspacePublishStaging.Preparation prep1 =
                    staging.materialize(List.of(provider, consumer), stagingRoot, OPTIONS);
            WorkspacePublishReport first = new WorkspaceRepositoryUploader()
                    .upload(prep1.members(), OPTIONS, Set.of(), statePath);
            assertFalse(first.ok());
            ResumeState state = ResumeState.read(statePath).state().orElseThrow();
            assertEquals(List.of("acme-http"), state.members(), "the emitted command omits the completed provider");
            assertEquals(List.of("acme-core", "acme-http"), state.familyMembers());

            String providerPath = "/maven2/com/acme/acme-core/1.0.0/acme-core-1.0.0" + extension;
            server.store.remove(providerPath);
            server.failPutPathSuffix = null;
            WorkspacePublishStaging.Preparation prep2 =
                    staging.materialize(List.of(provider, consumer), stagingRoot, OPTIONS, Optional.of(state));
            WorkspacePublishReport second =
                    new WorkspaceRepositoryUploader().upload(prep2.members(), OPTIONS, state.completed(), statePath);

            assertTrue(second.ok(), () -> "resume blockers: " + second.blockers());
            assertEquals(2, server.putCount(providerPath), "the omitted provider object is restored");
        } finally {
            server.close();
        }
    }

    private static MemberPublication httpJarMember(Path root, String name, String base) {
        Path memberRoot = root.resolve(name);
        writeFile(memberRoot.resolve("target/" + name + "-1.0.0.jar"), name + "-jar-contents");
        writeFile(memberRoot.resolve("target/publish/" + name + "-1.0.0.pom"), "<project/>");
        PublishRepositorySettings repo = new PublishRepositorySettings("local", base, Optional.empty());
        PublishSettings publish = new PublishSettings("local", "", List.of("main"), Map.of("local", repo));
        return new MemberPublication(
                memberRoot, name, "com.acme:" + name + ":1.0.0", false, jarPlan("com.acme:" + name + ":1.0.0", base),
                publish, Map.of());
    }

    private static MemberPublication signedJarMember(Path root, String name, String base) {
        Path memberRoot = root.resolve(name);
        writeFile(memberRoot.resolve("target/" + name + "-1.0.0.jar"), name + "-jar-contents");
        writeFile(memberRoot.resolve("target/publish/" + name + "-1.0.0.pom"), "<project/>");
        PublishRepositorySettings repo = new PublishRepositorySettings("local", base, Optional.empty());
        PublishSigningSettings signing =
                new PublishSigningSettings(true, Optional.empty(), Optional.of("ZOLT_TEST_GPG_PASS"));
        PublishSettings publish = new PublishSettings("local", "", List.of("main"), Map.of("local", repo), signing);
        return new MemberPublication(
                memberRoot, name, "com.acme:" + name + ":1.0.0", false, jarPlan("com.acme:" + name + ":1.0.0", base),
                publish, Map.of());
    }

    private static MemberPublication fileMember(Path memberRoot, String name, String repoUrl) {
        PublishRepositorySettings repo = new PublishRepositorySettings("local", repoUrl, Optional.empty());
        PublishSettings publish = new PublishSettings("local", "", List.of("main"), Map.of("local", repo));
        return new MemberPublication(
                memberRoot, name, "com.acme:" + name + ":1.0.0", false, jarPlan("com.acme:" + name + ":1.0.0", repoUrl),
                publish, Map.of());
    }

    private static PublishDryRunPlan jarPlan(String coordinate, String repoUrl) {
        String[] parts = coordinate.split(":");
        String base = parts[0].replace('.', '/') + "/" + parts[1] + "/" + parts[2] + "/" + parts[1] + "-" + parts[2];
        return new PublishDryRunPlan(
                coordinate,
                "release",
                "local",
                repoUrl,
                "main",
                Path.of("target/" + parts[1] + "-" + parts[2] + ".jar"),
                "sha256:jar",
                base + ".jar",
                List.<PublishArtifactPlan>of(),
                Path.of("target/publish/" + parts[1] + "-" + parts[2] + ".pom"),
                Path.of("target/publish/" + parts[1] + "-" + parts[2] + ".pom"),
                "sha256:pom",
                base + ".pom",
                List.of(),
                "",
                List.of(),
                false);
    }

    private static void writeFile(Path path, String content) {
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, content);
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
