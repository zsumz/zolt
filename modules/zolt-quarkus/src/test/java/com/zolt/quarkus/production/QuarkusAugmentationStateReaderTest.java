package com.zolt.quarkus.production;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.quarkus.QuarkusPlanException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class QuarkusAugmentationStateReaderTest {
    @TempDir
    private Path tempDir;

    private final QuarkusAugmentationStateReader reader = new QuarkusAugmentationStateReader();

    @Test
    void missingMetadataReportsMissing() {
        QuarkusAugmentationState state = reader.read(tempDir, "sha256:" + "1".repeat(64));

        assertEquals(QuarkusAugmentationState.Status.MISSING, state.status());
        assertEquals(tempDir.resolve("target/quarkus/zolt-augmentation.properties"), state.metadataPath());
    }

    @Test
    void matchingFingerprintReportsCurrent() throws IOException {
        writeMetadata("sha256:" + "1".repeat(64));

        QuarkusAugmentationState state = reader.read(tempDir, "sha256:" + "1".repeat(64));

        assertEquals(QuarkusAugmentationState.Status.CURRENT, state.status());
        assertEquals("sha256:" + "1".repeat(64), state.recordedInputFingerprint().orElseThrow());
    }

    @Test
    void differentFingerprintReportsStale() throws IOException {
        writeMetadata("sha256:" + "1".repeat(64));

        QuarkusAugmentationState state = reader.read(tempDir, "sha256:" + "2".repeat(64));

        assertEquals(QuarkusAugmentationState.Status.STALE, state.status());
    }

    @Test
    void malformedMetadataFailsClearly() throws IOException {
        Path metadata = tempDir.resolve("target/quarkus/zolt-augmentation.properties");
        Files.createDirectories(metadata.getParent());
        Files.writeString(metadata, "version=1\n");

        QuarkusPlanException exception = assertThrows(
                QuarkusPlanException.class,
                () -> reader.read(tempDir, "sha256:" + "1".repeat(64)));

        assertTrue(exception.getMessage().contains("Invalid Quarkus augmentation metadata"));
        assertTrue(exception.getMessage().contains("Remove it"));
    }

    private void writeMetadata(String fingerprint) throws IOException {
        Path metadata = tempDir.resolve("target/quarkus/zolt-augmentation.properties");
        Files.createDirectories(metadata.getParent());
        Files.writeString(metadata, """
                version=1
                inputFingerprint=%s
                """.formatted(fingerprint));
    }
}
