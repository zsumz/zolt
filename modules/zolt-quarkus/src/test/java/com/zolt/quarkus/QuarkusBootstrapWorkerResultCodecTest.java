package com.zolt.quarkus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class QuarkusBootstrapWorkerResultCodecTest {
    private final QuarkusBootstrapWorkerResultCodec codec = new QuarkusBootstrapWorkerResultCodec();

    @Test
    void writesAndParsesWorkerResult() {
        QuarkusBootstrapWorkerResult result = new QuarkusBootstrapWorkerResult(
                "sha256:" + "1".repeat(64),
                Path.of("/repo/target/quarkus-app"),
                Path.of("/repo/target/quarkus-app/quarkus-run.jar"),
                Path.of("/repo/target/quarkus-app/lib"),
                2);

        Optional<QuarkusBootstrapWorkerResult> parsed = codec.parse(write(result));

        assertTrue(parsed.isPresent());
        assertEquals(result, parsed.orElseThrow());
    }

    @Test
    void parsesResultSurroundedByOtherOutput() {
        QuarkusBootstrapWorkerResult result = new QuarkusBootstrapWorkerResult(
                "sha256:" + "1".repeat(64),
                Path.of("/repo/target/quarkus-app"),
                Path.of("/repo/target/quarkus-app/quarkus-run.jar"),
                null,
                1);

        Optional<QuarkusBootstrapWorkerResult> parsed = codec.parse("log before\n" + write(result) + "log after\n");

        assertTrue(parsed.isPresent());
        assertEquals(result, parsed.orElseThrow());
    }

    @Test
    void returnsEmptyWhenResultIsMissing() {
        assertTrue(codec.parse("plain output\n").isEmpty());
    }

    @Test
    void rejectsUnterminatedResult() {
        QuarkusAugmentationException exception = assertThrows(
                QuarkusAugmentationException.class,
                () -> codec.parse(QuarkusBootstrapWorkerResultCodec.BEGIN + "\nversion=1\n"));

        assertTrue(exception.getMessage().contains("was not terminated"));
    }

    @Test
    void rejectsMissingRequiredField() {
        String output = QuarkusBootstrapWorkerResultCodec.BEGIN
                + "\nversion=1\n"
                + "inputFingerprint=sha256:"
                + "1".repeat(64)
                + "\n"
                + "runnerJar=/repo/target/quarkus-app/quarkus-run.jar\n"
                + "artifactResultCount=1\n"
                + QuarkusBootstrapWorkerResultCodec.END
                + "\n";

        QuarkusAugmentationException exception = assertThrows(
                QuarkusAugmentationException.class,
                () -> codec.parse(output));

        assertTrue(exception.getMessage().contains("missing required field packageDirectory"));
    }

    @Test
    void rejectsInvalidArtifactResultCount() {
        String output = QuarkusBootstrapWorkerResultCodec.BEGIN
                + "\nversion=1\n"
                + "inputFingerprint=sha256:"
                + "1".repeat(64)
                + "\n"
                + "packageDirectory=/repo/target/quarkus-app\n"
                + "runnerJar=/repo/target/quarkus-app/quarkus-run.jar\n"
                + "artifactResultCount=bad\n"
                + QuarkusBootstrapWorkerResultCodec.END
                + "\n";

        QuarkusAugmentationException exception = assertThrows(
                QuarkusAugmentationException.class,
                () -> codec.parse(output));

        assertTrue(exception.getMessage().contains("invalid artifactResultCount"));
    }

    private String write(QuarkusBootstrapWorkerResult result) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        codec.write(new PrintStream(output, true, StandardCharsets.UTF_8), result);
        return output.toString(StandardCharsets.UTF_8);
    }
}
