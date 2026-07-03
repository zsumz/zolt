package sh.zolt.quality.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import sh.zolt.quality.execution.ExecutionSplitEvidence.ShardEvidenceManifest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ExecutionSplitEvidenceTest {
    private final ExecutionSplitEvidence evidence = new ExecutionSplitEvidence();

    @TempDir
    private Path tempDir;

    @Test
    void parsesShardManifestsAndSkipsMalformedNames() throws IOException {
        Path shards = tempDir.resolve("target/test-shards");
        Files.createDirectories(shards.resolve("unit-suite"));
        Files.writeString(shards.resolve("unit-suite/shard-2-of-4.json"), """
                {"suite": "unit suite", "empty": false}
                """);
        Files.writeString(shards.resolve("unit-suite/shard-nope.json"), "{}");
        Files.createDirectories(shards.resolve("too/deep"));
        Files.writeString(shards.resolve("too/deep/shard-1-of-2.json"), "{}");

        List<ShardEvidenceManifest> manifests = evidence.shardManifests(tempDir, Path.of("target"));

        assertEquals(1, manifests.size());
        ShardEvidenceManifest manifest = manifests.getFirst();
        assertEquals("unit suite", manifest.suiteName());
        assertEquals("unit-suite", manifest.suiteSegment());
        assertEquals("shard-2-of-4", manifest.shardSegment());
        assertEquals(2, manifest.index());
        assertEquals(4, manifest.total());
        assertEquals("unit suite/shard-2-of-4", manifest.displayName());
    }

    @Test
    void shardManifestSkipsMalformedShardSegments() throws IOException {
        Path shards = tempDir.resolve("target/test-shards/unit-suite");
        Files.createDirectories(shards);
        Files.writeString(shards.resolve("not-shard-1-of-2.json"), "{}");
        Files.writeString(shards.resolve("shard-1.json"), "{}");
        Files.writeString(shards.resolve("shard-one-of-two.json"), "{}");

        assertTrue(evidence.shardManifest(tempDir.resolve("target/test-shards"), shards.resolve("not-shard-1-of-2.json")).isEmpty());
        assertTrue(evidence.shardManifest(tempDir.resolve("target/test-shards"), shards.resolve("shard-1.json")).isEmpty());
        assertTrue(evidence.shardManifest(tempDir.resolve("target/test-shards"), shards.resolve("shard-one-of-two.json")).isEmpty());
    }

    @Test
    void shardManifestParsesEscapedSuiteNames() throws IOException {
        Path manifest = tempDir.resolve("target/test-shards/escaped-suite/shard-1-of-1.json");
        Files.createDirectories(manifest.getParent());
        Files.writeString(manifest, "{\"suite\": \"line\\nquote\\\"tab\\tbackslash\\\\\", \"empty\": false}\n");

        ShardEvidenceManifest parsed = evidence.shardManifest(tempDir.resolve("target/test-shards"), manifest)
                .orElseThrow();

        assertEquals("line\nquote\"tab\tbackslash\\", parsed.suiteName());
        assertEquals("line\nquote\"tab\tbackslash\\/shard-1-of-1", parsed.displayName());
    }

    @Test
    void filtersEmptyShardManifests() throws IOException {
        Path shards = tempDir.resolve("target/test-shards/unit-suite");
        Files.createDirectories(shards);
        Files.writeString(shards.resolve("shard-1-of-2.json"), """
                {"suite": "unit", "empty": true}
                """);
        Files.writeString(shards.resolve("shard-2-of-2.json"), """
                {"suite": "unit", "empty": false}
                """);

        List<ShardEvidenceManifest> nonEmpty = evidence.nonEmpty(evidence.shardManifests(tempDir, Path.of("target")));

        assertEquals(List.of("shard-2-of-2"), nonEmpty.stream()
                .map(ShardEvidenceManifest::shardSegment)
                .toList());
    }

    @Test
    void workerIdsParseQuotedValuesOnly() throws IOException {
        Path manifest = tempDir.resolve("workers/zolt-workers.json");
        Files.createDirectories(manifest.getParent());
        Files.writeString(manifest, """
                {"workers": ["worker-a", 42, "worker-b"]}
                """);

        assertEquals(List.of("worker-a", "worker-b"), evidence.workerIds(manifest));
        assertEquals(List.of(), evidence.workerIds(tempDir.resolve("missing.json")));
    }

    @Test
    void workerIdsSkipMalformedWorkerManifestShapes() throws IOException {
        Path manifest = tempDir.resolve("workers/zolt-workers.json");
        Files.createDirectories(manifest.getParent());

        Files.writeString(manifest, """
                {"notWorkers": ["worker-a"]}
                """);
        assertEquals(List.of(), evidence.workerIds(manifest));

        Files.writeString(manifest, """
                {"workers": "worker-a"}
                """);
        assertEquals(List.of(), evidence.workerIds(manifest));

        Files.writeString(manifest, """
                {"workers": ["worker-a"
                """);
        assertEquals(List.of(), evidence.workerIds(manifest));
    }

    @Test
    void shellArgumentQuotesValuesThatAreUnsafeForShellLogs() {
        assertEquals("unit-suite", evidence.shellArgument("unit-suite"));
        assertEquals("\"unit suite\"", evidence.shellArgument("unit suite"));
        assertEquals("\"quote\\\"and\\\\slash\"", evidence.shellArgument("quote\"and\\slash"));
        assertEquals("\"\"", evidence.shellArgument(null));
    }

    @Test
    void shardManifestFallsBackToSuiteSegmentWhenJsonLacksSuiteName() throws IOException {
        Path manifest = tempDir.resolve("target/test-shards/fast/shard-1-of-1.json");
        Files.createDirectories(manifest.getParent());
        Files.writeString(manifest, "{}");

        ShardEvidenceManifest parsed = evidence.shardManifest(tempDir.resolve("target/test-shards"), manifest)
                .orElseThrow();

        assertEquals("fast", parsed.suiteName());
        assertTrue(evidence.shardManifest(tempDir.resolve("target/test-shards"), tempDir.resolve("target/test-shards/fast/not-a-shard.json")).isEmpty());
    }
}
