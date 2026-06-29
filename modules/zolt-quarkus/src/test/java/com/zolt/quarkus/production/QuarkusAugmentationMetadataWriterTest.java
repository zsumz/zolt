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

final class QuarkusAugmentationMetadataWriterTest {
    @TempDir
    private Path tempDir;

    private final QuarkusAugmentationMetadataWriter writer = new QuarkusAugmentationMetadataWriter();
    private final QuarkusAugmentationStateReader reader = new QuarkusAugmentationStateReader();

    @Test
    void writesDeterministicMetadata() throws IOException {
        String fingerprint = "sha256:" + "1".repeat(64);

        writer.write(tempDir, fingerprint);

        Path metadata = tempDir.resolve("target/quarkus/zolt-augmentation.properties");
        assertEquals("""
                version=1
                inputFingerprint=%s
                """.formatted(fingerprint), Files.readString(metadata));
    }

    @Test
    void writtenMetadataReadsAsCurrent() {
        String fingerprint = "sha256:" + "2".repeat(64);

        writer.write(tempDir, ".zolt/build", fingerprint);

        QuarkusAugmentationState state = reader.read(tempDir, ".zolt/build", fingerprint);
        assertEquals(QuarkusAugmentationState.Status.CURRENT, state.status());
        assertEquals(fingerprint, state.recordedInputFingerprint().orElseThrow());
        assertEquals(tempDir.resolve(".zolt/build/quarkus/zolt-augmentation.properties"), state.metadataPath());
    }

    @Test
    void rejectsBlankFingerprint() {
        QuarkusPlanException exception = assertThrows(
                QuarkusPlanException.class,
                () -> writer.write(tempDir, " "));

        assertTrue(exception.getMessage().contains("requires an input fingerprint"));
    }
}
