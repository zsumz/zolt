package sh.zolt.workspace.publish;

import java.nio.file.Path;

/**
 * One pre-staged file in the Phase-2 upload set: the Maven repository-relative path it lands at, the
 * local file whose bytes are uploaded verbatim, and {@code stagedSha256} — the lowercase-hex SHA-256
 * of exactly those staged bytes, captured in Phase 1. The digest anchors the transaction manifest: a
 * resume verifies a completed path against it (the artifact's bytes must still match what we staged)
 * and reuses a staged signature only while its on-disk bytes still hash to it. Bytes are produced in
 * Phase 1 — a built artifact/POM, or a checksum/signature materialized into the staging directory — so
 * Phase 2 is a pure transfer with no signing or digesting left to fail mid-member.
 */
record StagedArtifact(String repositoryPath, Path source, String stagedSha256) {
}
