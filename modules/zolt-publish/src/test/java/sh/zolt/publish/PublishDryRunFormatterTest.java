package sh.zolt.publish;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class PublishDryRunFormatterTest {
    @Test
    void rendersReadyBranchWithSupplementalArtifacts() {
        PublishArtifactPlan sources = new PublishArtifactPlan(
                "sources",
                Optional.of("sources"),
                Path.of("/work/target/app-1.0.0-sources.jar"),
                "sha256:aaa",
                "com/example/app/1.0.0/app-1.0.0-sources.jar");
        PublishDryRunPlan plan = new PublishDryRunPlan(
                "com.example:app:1.0.0",
                "release",
                "company-releases",
                "https://repo.example.test/releases",
                "main",
                Path.of("/work/target/app-1.0.0.jar"),
                "sha256:main",
                "com/example/app/1.0.0/app-1.0.0.jar",
                List.of(sources),
                Path.of("/work/target/evidence.json"),
                Path.of("/work/target/app-1.0.0.pom"),
                "sha256:pom",
                "com/example/app/1.0.0/app-1.0.0.pom",
                List.of(
                        new PublishChecksumSidecar(
                                "artifact", "sha1", "com/example/app/1.0.0/app-1.0.0.jar.sha1", "a1a1"),
                        new PublishChecksumSidecar(
                                "pom", "sha1", "com/example/app/1.0.0/app-1.0.0.pom.sha1", "b2b2")),
                "release",
                List.of());

        String text = PublishDryRunFormatter.text(plan);

        assertEquals("""
                Zolt publish dry run
                Coordinate: com.example:app:1.0.0
                Version kind: release
                Context: release
                Policy source: built-in release context
                Target repository: company-releases
                Target URL: https://repo.example.test/releases
                Artifact: main
                Artifact path: /work/target/app-1.0.0.jar
                Artifact checksum: sha256:main
                Artifact upload path: com/example/app/1.0.0/app-1.0.0.jar
                Supplemental artifacts:
                - sources: /work/target/app-1.0.0-sources.jar
                  checksum: sha256:aaa
                  upload path: com/example/app/1.0.0/app-1.0.0-sources.jar
                Evidence: /work/target/evidence.json
                Generated POM: /work/target/app-1.0.0.pom
                POM checksum: sha256:pom
                POM upload path: com/example/app/1.0.0/app-1.0.0.pom
                Checksum sidecars:
                - com/example/app/1.0.0/app-1.0.0.jar.sha1: a1a1
                - com/example/app/1.0.0/app-1.0.0.pom.sha1: b2b2
                Status: ready
                No upload was performed.
                """, text);
    }

    @Test
    void rendersBlockedBranchListingBlockers() {
        PublishDryRunPlan plan = new PublishDryRunPlan(
                "com.example:app:1.0.0-SNAPSHOT",
                "snapshot",
                "company-snapshots",
                "https://repo.example.test/snapshots",
                "main",
                Path.of("/work/target/app.jar"),
                "sha256:main",
                "upload/app.jar",
                List.of(),
                Path.of("/work/target/evidence.json"),
                Path.of("/work/target/app.pom"),
                "sha256:pom",
                "upload/app.pom",
                List.of(),
                "",
                List.of("Release repository is not configured.", "Credentials are missing."));

        String text = PublishDryRunFormatter.text(plan);

        assertTrue(text.contains("Status: blocked\n"));
        assertTrue(text.contains("- Release repository is not configured.\n"));
        assertTrue(text.contains("- Credentials are missing.\n"));
        // Blank context suppresses the Context/Policy lines and the ready footer.
        assertTrue(!text.contains("Context: "));
        assertTrue(!text.contains("Status: ready"));
        assertTrue(!text.contains("Supplemental artifacts:"));
        assertTrue(!text.contains("Checksum sidecars:"));
    }
}
