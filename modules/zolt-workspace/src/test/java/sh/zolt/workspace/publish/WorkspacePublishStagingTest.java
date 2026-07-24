package sh.zolt.workspace.publish;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import sh.zolt.publish.PublishArtifactPlan;
import sh.zolt.publish.PublishDryRunPlan;
import sh.zolt.publish.PublishRepositorySettings;
import sh.zolt.publish.PublishSettings;
import sh.zolt.publish.PublishSigningSettings;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Phase-1 materialization gates: a repository-URL-policy violation and an unmet signing prerequisite
 * must each block the family with nothing staged — so the service uploads nothing and no network
 * request is ever made (staging is purely local; the uploader only runs on staged members).
 */
final class WorkspacePublishStagingTest {
    private static final WorkspacePublishService.Options OPTIONS =
            new WorkspacePublishService.Options(false, false, false, false, Optional.empty());

    @Test
    void remoteHttpRepositoryViolatesTheUrlPolicyInPhaseOne(@TempDir Path tempDir) {
        // Plain HTTP to a non-loopback host is rejected by the same policy Phase 2 applies per request.
        PublishRepositorySettings repo =
                new PublishRepositorySettings("remote", "http://repo.example.test/releases", Optional.empty());
        PublishSettings publish = new PublishSettings("remote", "", List.of("main"), Map.of("remote", repo));
        MemberPublication member = new MemberPublication(
                tempDir.resolve("acme-core"),
                "acme-core",
                "com.acme:acme-core:1.0.0",
                false,
                jarPlan("com.acme:acme-core:1.0.0", "http://repo.example.test/releases"),
                publish,
                Map.of());

        WorkspacePublishStaging.Preparation preparation =
                new WorkspacePublishStaging().materialize(List.of(member), tempDir.resolve("staging"), OPTIONS);

        assertFalse(preparation.blockers().isEmpty());
        assertTrue(preparation.members().isEmpty(), "nothing is staged, so nothing uploads");
        assertTrue(preparation.blockers().get(0).contains("com.acme:acme-core:1.0.0"), preparation.blockers()::toString);
    }

    @Test
    void missingSigningPassphraseBlocksInPhaseOneBeforeStaging(@TempDir Path tempDir) {
        PublishSigningSettings signing =
                new PublishSigningSettings(true, Optional.empty(), Optional.of("ZOLT_UNSET_PASSPHRASE"));
        PublishRepositorySettings repo = new PublishRepositorySettings(
                "local", tempDir.resolve("repo").toUri().toString(), Optional.empty());
        PublishSettings publish = new PublishSettings("local", "", List.of("main"), Map.of("local", repo), signing);
        MemberPublication member = new MemberPublication(
                tempDir.resolve("acme-core"),
                "acme-core",
                "com.acme:acme-core:1.0.0",
                false,
                jarPlan("com.acme:acme-core:1.0.0", tempDir.resolve("repo").toUri().toString()),
                publish,
                Map.of());
        // The passphrase env var is unset, so signing cannot run — surfaced before any artifact is staged.
        WorkspacePublishStaging staging = new WorkspacePublishStaging(name -> null);

        WorkspacePublishStaging.Preparation preparation =
                staging.materialize(List.of(member), tempDir.resolve("staging"), OPTIONS);

        assertFalse(preparation.blockers().isEmpty());
        assertTrue(preparation.members().isEmpty(), "nothing is staged, so nothing uploads");
        String blocker = preparation.blockers().get(0);
        assertTrue(blocker.contains("signing preflight"), blocker);
        assertTrue(blocker.contains("ZOLT_UNSET_PASSPHRASE"), blocker);
    }

    @Test
    void aCleanFamilyStagesEveryMemberWithNoBlockers(@TempDir Path tempDir) throws Exception {
        Path memberRoot = tempDir.resolve("acme-core");
        java.nio.file.Files.createDirectories(memberRoot.resolve("target/publish"));
        java.nio.file.Files.writeString(memberRoot.resolve("target/acme-core-1.0.0.jar"), "jar-bytes");
        java.nio.file.Files.writeString(memberRoot.resolve("target/publish/acme-core-1.0.0.pom"), "<project/>");
        PublishRepositorySettings repo = new PublishRepositorySettings(
                "local", tempDir.resolve("repo").toUri().toString(), Optional.empty());
        PublishSettings publish = new PublishSettings("local", "", List.of("main"), Map.of("local", repo));
        MemberPublication member = new MemberPublication(
                memberRoot,
                "acme-core",
                "com.acme:acme-core:1.0.0",
                false,
                jarPlan("com.acme:acme-core:1.0.0", tempDir.resolve("repo").toUri().toString()),
                publish,
                Map.of());

        WorkspacePublishStaging.Preparation preparation =
                new WorkspacePublishStaging().materialize(List.of(member), tempDir.resolve("staging"), OPTIONS);

        assertTrue(preparation.blockers().isEmpty(), () -> "blockers: " + preparation.blockers());
        assertEquals(1, preparation.members().size());
        // The jar + its three checksums and the POM + its three checksums are all staged (8 files).
        assertEquals(8, preparation.members().get(0).artifacts().size());
    }

    private static PublishDryRunPlan jarPlan(String coord, String repoUrl) {
        String[] parts = coord.split(":");
        String group = parts[0].replace('.', '/');
        String name = parts[1];
        String version = parts[2];
        String base = group + "/" + name + "/" + version + "/" + name + "-" + version;
        String repositoryId = repoUrl.startsWith("file:") ? "local" : "remote";
        return new PublishDryRunPlan(
                coord,
                "release",
                repositoryId,
                repoUrl,
                "main",
                Path.of("target/" + name + "-" + version + ".jar"),
                "sha256:jar",
                base + ".jar",
                List.<PublishArtifactPlan>of(),
                Path.of("target/publish/" + name + "-" + version + ".pom"),
                Path.of("target/publish/" + name + "-" + version + ".pom"),
                "sha256:pom",
                base + ".pom",
                List.of(),
                "",
                List.of(),
                false);
    }
}
