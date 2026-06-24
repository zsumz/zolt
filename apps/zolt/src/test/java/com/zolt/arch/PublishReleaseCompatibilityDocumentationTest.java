package com.zolt.arch;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;

final class PublishReleaseCompatibilityDocumentationTest {
    @Test
    void publishReleaseCompatibilityDocumentNamesLocalSmokeAndContracts() throws IOException {
        String document = Files.readString(RepositoryPaths.root().resolve("docs/publish-release-compatibility.md"));

        assertTrue(document.contains("`scripts/smoke-publish-release-compatibility`"));
        assertTrue(document.contains("Run `zolt init` and `zolt build` from a clean quickstart directory"));
        assertTrue(document.contains("Run `zolt resolve`, `zolt package`, and `zolt publish --dry-run`"));
        assertTrue(document.contains("Recompute SHA-256 for the artifact and POM"));
        assertTrue(document.contains("Materialize the artifact, POM, and `.sha256` sidecars"));
        assertTrue(document.contains("Compile and run a downstream Java consumer"));
        assertTrue(document.contains("Run a previous/current/previous rollback harness"));
        assertTrue(document.contains("`ZOLT_PREVIOUS_ZOLT`"));
        assertTrue(document.contains("Remote `zolt publish` upload"));
    }

    @Test
    void releaseDocsAndMatrixLinkPublishReleaseCompatibility() throws IOException {
        String docsIndex = Files.readString(RepositoryPaths.root().resolve("docs/README.md"));
        String releasePackaging = Files.readString(RepositoryPaths.root().resolve("docs/release-packaging.md"));
        String testMatrix = Files.readString(RepositoryPaths.root().resolve("docs/test-pattern-matrix.md"));

        assertTrue(docsIndex.contains("`publish-release-compatibility.md`"));
        assertTrue(releasePackaging.contains("`publish-release-compatibility.md`"));
        assertTrue(releasePackaging.contains("`scripts/smoke-publish-release-compatibility` passes"));
        assertTrue(testMatrix.contains("`scripts/smoke-publish-release-compatibility`"));
        assertTrue(testMatrix.contains("downstream Java consumer"));
        assertTrue(testMatrix.contains("clear safe-invalidation diagnostic"));
    }
}
