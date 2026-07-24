package sh.zolt.workspace.publish;

import java.nio.file.Path;

/**
 * One pre-staged file in the Phase-2 upload set: the Maven repository-relative path it lands at and
 * the local file whose bytes are uploaded verbatim. The bytes are produced in Phase 1 — a built
 * artifact/POM, or a checksum/signature materialized into the staging directory — so Phase 2 is a
 * pure transfer with no signing or digesting left to fail mid-member.
 */
record StagedArtifact(String repositoryPath, Path source) {
}
