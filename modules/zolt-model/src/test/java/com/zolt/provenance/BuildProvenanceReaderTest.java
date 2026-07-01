package com.zolt.provenance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Verifies {@link BuildProvenanceReader} assembles provenance from injected inputs deterministically. */
final class BuildProvenanceReaderTest {

    private static final String SHA_MAIN = "1111111111111111111111111111111111111111";
    private static final Instant CLOCK_NOW = Instant.parse("2026-07-01T12:00:00Z");

    private final BuildProvenanceReader reader =
            new BuildProvenanceReader(new GitProvenanceReader(), jdkProps("21.0.2", "Eclipse Adoptium"));

    @Test
    void assemblesGitTimestampToolchainAndFingerprint(@TempDir Path root) throws IOException {
        gitRepo(root);

        BuildProvenance provenance = reader.read(
                root,
                "0.1.0-SNAPSHOT",
                Optional.of("sha256:deadbeef"),
                Map.of(),
                fixedClock());

        assertEquals(Optional.of(SHA_MAIN), provenance.git().commitSha());
        assertEquals(Optional.of("main"), provenance.git().branch());
        assertEquals(CLOCK_NOW, provenance.buildTimestamp());
        assertEquals("0.1.0-SNAPSHOT", provenance.zoltVersion());
        assertEquals("21.0.2", provenance.jdkVersion());
        assertEquals("Eclipse Adoptium", provenance.jdkVendor());
        assertEquals(Optional.of("sha256:deadbeef"), provenance.resolutionFingerprint());
    }

    @Test
    void sourceDateEpochIsHonored(@TempDir Path root) throws IOException {
        gitRepo(root);

        BuildProvenance provenance = reader.read(
                root,
                "0.1.0-SNAPSHOT",
                Optional.empty(),
                Map.of(BuildProvenanceReader.SOURCE_DATE_EPOCH, "1700000000"),
                fixedClock());

        assertEquals(Instant.ofEpochSecond(1_700_000_000L), provenance.buildTimestamp());
    }

    @Test
    void clockIsUsedWhenSourceDateEpochAbsent(@TempDir Path root) throws IOException {
        gitRepo(root);

        BuildProvenance provenance = reader.read(
                root, "0.1.0-SNAPSHOT", Optional.empty(), Map.of(), fixedClock());

        assertEquals(CLOCK_NOW, provenance.buildTimestamp());
    }

    @Test
    void malformedSourceDateEpochFallsBackToClock(@TempDir Path root) throws IOException {
        gitRepo(root);

        BuildProvenance provenance = reader.read(
                root,
                "0.1.0-SNAPSHOT",
                Optional.empty(),
                Map.of(BuildProvenanceReader.SOURCE_DATE_EPOCH, "not-a-number"),
                fixedClock());

        assertEquals(CLOCK_NOW, provenance.buildTimestamp());
    }

    @Test
    void blankSourceDateEpochFallsBackToClock(@TempDir Path root) throws IOException {
        gitRepo(root);

        BuildProvenance provenance = reader.read(
                root,
                "0.1.0-SNAPSHOT",
                Optional.empty(),
                Map.of(BuildProvenanceReader.SOURCE_DATE_EPOCH, "   "),
                fixedClock());

        assertEquals(CLOCK_NOW, provenance.buildTimestamp());
    }

    @Test
    void notARepoYieldsNoneGitButFullMetadata(@TempDir Path root) {
        BuildProvenance provenance = reader.read(
                root, "0.1.0-SNAPSHOT", Optional.empty(), Map.of(), fixedClock());

        assertTrue(provenance.git().commitSha().isEmpty());
        assertTrue(provenance.git().branch().isEmpty());
        assertEquals("0.1.0-SNAPSHOT", provenance.zoltVersion());
        assertEquals(CLOCK_NOW, provenance.buildTimestamp());
    }

    @Test
    void missingFingerprintStaysEmpty(@TempDir Path root) {
        BuildProvenance provenance = reader.read(
                root, "0.1.0-SNAPSHOT", Optional.empty(), Map.of(), fixedClock());

        assertTrue(provenance.resolutionFingerprint().isEmpty());
    }

    @Test
    void dirtyIsUnknownInV1(@TempDir Path root) throws IOException {
        gitRepo(root);

        BuildProvenance provenance = reader.read(
                root, "0.1.0-SNAPSHOT", Optional.empty(), Map.of(), fixedClock());

        assertTrue(provenance.git().dirty().isEmpty());
    }

    @Test
    void outputIsDeterministic(@TempDir Path root) throws IOException {
        gitRepo(root);

        BuildProvenance first = reader.read(
                root,
                "0.1.0-SNAPSHOT",
                Optional.of("sha256:abc"),
                Map.of(BuildProvenanceReader.SOURCE_DATE_EPOCH, "1700000000"),
                fixedClock());
        BuildProvenance second = reader.read(
                root,
                "0.1.0-SNAPSHOT",
                Optional.of("sha256:abc"),
                Map.of(BuildProvenanceReader.SOURCE_DATE_EPOCH, "1700000000"),
                fixedClock());

        assertEquals(first, second);
    }

    @Test
    void defaultConstructorReadsSystemProperties(@TempDir Path root) {
        BuildProvenanceReader defaultReader = new BuildProvenanceReader();

        BuildProvenance provenance = defaultReader.read(
                root, "0.1.0", Optional.empty(), Map.of(), fixedClock());

        // Real JVM values are present; we only assert they are populated, not their exact content.
        assertEquals(System.getProperty("java.version"), provenance.jdkVersion());
        assertEquals(System.getProperty("java.vendor"), provenance.jdkVendor());
    }

    private static void gitRepo(Path root) throws IOException {
        Path gitDir = Files.createDirectory(root.resolve(".git"));
        Files.writeString(gitDir.resolve("HEAD"), "ref: refs/heads/main\n", StandardCharsets.UTF_8);
        Path ref = gitDir.resolve("refs").resolve("heads");
        Files.createDirectories(ref);
        Files.writeString(ref.resolve("main"), SHA_MAIN + "\n", StandardCharsets.UTF_8);
    }

    private static Clock fixedClock() {
        return Clock.fixed(CLOCK_NOW, ZoneOffset.UTC);
    }

    private static Properties jdkProps(String version, String vendor) {
        Properties props = new Properties();
        props.setProperty("java.version", version);
        props.setProperty("java.vendor", vendor);
        return props;
    }
}
