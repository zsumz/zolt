package com.zolt.quarkus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.project.QuarkusPackageMode;
import com.zolt.resolve.DependencyScope;
import com.zolt.resolve.PackageId;
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
        AtomicReference<QuarkusBootstrapDescriptor> seenDescriptor = new AtomicReference<>();
        QuarkusAugmentationRequest request = request();
        QuarkusAugmentationExecutor executor = new QuarkusAugmentationExecutor((augmentRequest, descriptor) -> {
            seenRequest.set(augmentRequest);
            seenDescriptor.set(descriptor);
            return workerResult(augmentRequest);
        });

        QuarkusAugmentationResult result = executor.augment(request);

        assertSame(request, seenRequest.get());
        assertEquals(projectDir.resolve("target/quarkus/zolt-bootstrap.properties"), seenDescriptor.get().descriptorFile());
        assertTrue(Files.isDirectory(projectDir.resolve("target/quarkus")));
        assertEquals(projectDir.resolve("target/quarkus"), result.augmentationDirectory());
        assertEquals(projectDir.resolve("target/quarkus/zolt-augmentation.properties"), result.metadataPath());
        assertEquals(projectDir.resolve("target/quarkus/zolt-bootstrap.properties"), result.bootstrapDescriptor().descriptorFile());
        assertTrue(Files.exists(projectDir.resolve("target/quarkus/runtime-classpath.txt")));
        assertTrue(Files.exists(projectDir.resolve("target/quarkus/deployment-classpath.txt")));
        assertTrue(Files.exists(projectDir.resolve("target/quarkus/application-model.properties")));
        assertEquals(request.inputFingerprint(), result.inputFingerprint());
        assertEquals(workerResult(request), result.workerResult());
        QuarkusAugmentationState state = new QuarkusAugmentationStateReader()
                .read(projectDir, request.inputFingerprint());
        assertEquals(QuarkusAugmentationState.Status.CURRENT, state.status());
    }

    @Test
    void doesNotWriteMetadataWhenAugmentorFails() {
        QuarkusAugmentationRequest request = request();
        QuarkusAugmentationExecutor executor = new QuarkusAugmentationExecutor((ignored, descriptor) -> {
            throw new QuarkusAugmentationException("augmentation failed");
        });

        QuarkusAugmentationException exception = assertThrows(
                QuarkusAugmentationException.class,
                () -> executor.augment(request));

        assertEquals("augmentation failed", exception.getMessage());
        assertTrue(Files.isDirectory(projectDir.resolve("target/quarkus")));
        assertTrue(Files.exists(projectDir.resolve("target/quarkus/zolt-bootstrap.properties")));
        assertFalse(Files.exists(projectDir.resolve("target/quarkus/zolt-augmentation.properties")));
    }

    @Test
    void doesNotWriteMetadataWhenAugmentorReturnsNoWorkerResult() {
        QuarkusAugmentationRequest request = request();
        QuarkusAugmentationExecutor executor = new QuarkusAugmentationExecutor((ignored, descriptor) -> null);

        QuarkusAugmentationException exception = assertThrows(
                QuarkusAugmentationException.class,
                () -> executor.augment(request));

        assertTrue(exception.getMessage().contains("did not return a verified worker result"));
        assertFalse(Files.exists(projectDir.resolve("target/quarkus/zolt-augmentation.properties")));
    }

    @Test
    void doesNotWriteMetadataWhenWorkerResultFingerprintDoesNotMatch() {
        QuarkusAugmentationRequest request = request();
        QuarkusAugmentationExecutor executor = new QuarkusAugmentationExecutor((ignored, descriptor) ->
                new QuarkusBootstrapWorkerResult(
                        "sha256:" + "2".repeat(64),
                        request.outputLayout().packageDirectory(),
                        request.outputLayout().packageDirectory().resolve("quarkus-run.jar"),
                        request.outputLayout().packageDirectory().resolve("lib"),
                        1));

        QuarkusAugmentationException exception = assertThrows(
                QuarkusAugmentationException.class,
                () -> executor.augment(request));

        assertTrue(exception.getMessage().contains("did not match expected fingerprint"));
        assertFalse(Files.exists(projectDir.resolve("target/quarkus/zolt-augmentation.properties")));
    }

    @Test
    void doesNotWriteMetadataWhenWorkerResultPackageDirectoryDoesNotMatch() {
        QuarkusAugmentationRequest request = request();
        QuarkusAugmentationExecutor executor = new QuarkusAugmentationExecutor((ignored, descriptor) ->
                new QuarkusBootstrapWorkerResult(
                        request.inputFingerprint(),
                        projectDir.resolve("target/other-quarkus-app"),
                        projectDir.resolve("target/other-quarkus-app/quarkus-run.jar"),
                        projectDir.resolve("target/other-quarkus-app/lib"),
                        1));

        QuarkusAugmentationException exception = assertThrows(
                QuarkusAugmentationException.class,
                () -> executor.augment(request));

        assertTrue(exception.getMessage().contains("did not match expected package directory"));
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
        QuarkusAugmentationExecutor executor = new QuarkusAugmentationExecutor((ignored, descriptor) -> workerResult(ignored));

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
                new QuarkusApplicationArtifact(
                        new PackageId("com.example", "demo"),
                        "1.0.0",
                        projectDir.resolve("target/classes")),
                "sha256:" + "1".repeat(64),
                projectDir.resolve("target/quarkus/zolt-augmentation.properties"),
                List.of(projectDir.resolve(".zolt/cache/io/quarkus/quarkus-rest.jar")),
                List.of(projectDir.resolve(".zolt/cache/io/quarkus/quarkus-rest-deployment.jar")),
                List.of(),
                List.of(
                        new QuarkusBootstrapDependency(
                                new PackageId("io.quarkus", "quarkus-rest"),
                                "3.33.0",
                                DependencyScope.COMPILE,
                                projectDir.resolve(".zolt/cache/io/quarkus/quarkus-rest.jar"),
                                true),
                        new QuarkusBootstrapDependency(
                                new PackageId("io.quarkus", "quarkus-rest-deployment"),
                                "3.33.0",
                                DependencyScope.QUARKUS_DEPLOYMENT,
                                projectDir.resolve(".zolt/cache/io/quarkus/quarkus-rest-deployment.jar"),
                                false)),
                List.of());
    }

    private static QuarkusBootstrapWorkerResult workerResult(QuarkusAugmentationRequest request) {
        return new QuarkusBootstrapWorkerResult(
                request.inputFingerprint(),
                request.outputLayout().packageDirectory(),
                request.outputLayout().packageDirectory().resolve("quarkus-run.jar"),
                request.outputLayout().packageDirectory().resolve("lib"),
                1);
    }
}
