package com.zolt.quarkus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.project.QuarkusPackageMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class QuarkusAugmentationExecutorTest {
    @TempDir
    private Path projectDir;

    @Test
    void preparesOutputRunsAugmentorThenWritesCurrentMetadata() {
        AtomicReference<QuarkusAugmentationRequest> seenRequest = new AtomicReference<>();
        QuarkusAugmentationRequest request = request();
        QuarkusAugmentationExecutor executor = new QuarkusAugmentationExecutor(seenRequest::set);

        QuarkusAugmentationResult result = executor.augment(request);

        assertSame(request, seenRequest.get());
        assertTrue(Files.isDirectory(projectDir.resolve("target/quarkus")));
        assertEquals(projectDir.resolve("target/quarkus"), result.augmentationDirectory());
        assertEquals(projectDir.resolve("target/quarkus/zolt-augmentation.properties"), result.metadataPath());
        assertEquals(request.inputFingerprint(), result.inputFingerprint());
        QuarkusAugmentationState state = new QuarkusAugmentationStateReader()
                .read(projectDir, request.inputFingerprint());
        assertEquals(QuarkusAugmentationState.Status.CURRENT, state.status());
    }

    @Test
    void doesNotWriteMetadataWhenAugmentorFails() {
        QuarkusAugmentationRequest request = request();
        QuarkusAugmentationExecutor executor = new QuarkusAugmentationExecutor(ignored -> {
            throw new QuarkusAugmentationException("augmentation failed");
        });

        QuarkusAugmentationException exception = assertThrows(
                QuarkusAugmentationException.class,
                () -> executor.augment(request));

        assertEquals("augmentation failed", exception.getMessage());
        assertTrue(Files.isDirectory(projectDir.resolve("target/quarkus")));
        assertFalse(Files.exists(projectDir.resolve("target/quarkus/zolt-augmentation.properties")));
    }

    @Test
    void requiresAugmentor() {
        QuarkusAugmentationException exception = assertThrows(
                QuarkusAugmentationException.class,
                () -> new QuarkusAugmentationExecutor(null));

        assertTrue(exception.getMessage().contains("augmentor is required"));
    }

    @Test
    void requiresRequest() {
        QuarkusAugmentationExecutor executor = new QuarkusAugmentationExecutor(ignored -> {
        });

        QuarkusAugmentationException exception = assertThrows(
                QuarkusAugmentationException.class,
                () -> executor.augment(null));

        assertTrue(exception.getMessage().contains("request is required"));
    }

    private QuarkusAugmentationRequest request() {
        return new QuarkusAugmentationRequest(
                projectDir,
                projectDir.resolve("target/classes"),
                QuarkusPackageMode.FAST_JAR,
                new QuarkusOutputLayout(
                        projectDir.resolve("target/quarkus"),
                        projectDir.resolve("target/quarkus-app")),
                "sha256:" + "1".repeat(64),
                projectDir.resolve("target/quarkus/zolt-augmentation.properties"),
                List.of(projectDir.resolve(".zolt/cache/io/quarkus/quarkus-rest.jar")),
                List.of(projectDir.resolve(".zolt/cache/io/quarkus/quarkus-rest-deployment.jar")),
                List.of());
    }
}
