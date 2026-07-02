package sh.zolt.publish;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class PublishChecksumTest {
    @TempDir
    Path tempDir;

    @Test
    void returnsPrefixedHexDigestForKnownContent() throws IOException {
        Path artifact = tempDir.resolve("artifact.jar");
        // Fixed UTF-8 bytes so the expected digest is deterministic across platforms.
        Files.write(artifact, "zolt-publish artifact contents\n".getBytes(StandardCharsets.UTF_8));

        String checksum = PublishChecksum.sha256(artifact);

        assertEquals(
                "sha256:a19b335775ac59334938d443bd4e8e183addfc7c3013b151ae0bac854f0425ab",
                checksum);
    }

    @Test
    void emptyFileHashesToTheKnownEmptyDigest() throws IOException {
        Path artifact = tempDir.resolve("empty.jar");
        Files.write(artifact, new byte[0]);

        String checksum = PublishChecksum.sha256(artifact);

        assertEquals(
                "sha256:e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                checksum);
    }

    @Test
    void missingPathRaisesPublishExceptionWithRemediation() {
        Path missing = tempDir.resolve("does-not-exist.jar");

        PublishException exception = assertThrows(PublishException.class, () -> PublishChecksum.sha256(missing));

        assertTrue(exception.getMessage().contains("Could not read package artifact"));
        assertTrue(exception.getMessage().contains(missing.toString()));
    }
}
