package com.zolt.resolve.materialization.session;

import com.zolt.cache.CachedArtifact;
import com.zolt.concurrent.RepositoryExecutionLane;
import com.zolt.maven.ArtifactDescriptor;
import com.zolt.resolve.ResolveException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.Function;

final class ArtifactBatchMaterializer {
    Map<ArtifactDescriptor, CachedArtifact> materialize(
            List<ArtifactDescriptor> descriptors,
            int concurrency,
            Function<ArtifactDescriptor, CachedArtifact> materializer) {
        return materialize(descriptors, concurrency, RepositoryExecutionLane.DEFAULT, materializer);
    }

    Map<ArtifactDescriptor, CachedArtifact> materialize(
            List<ArtifactDescriptor> descriptors,
            int concurrency,
            RepositoryExecutionLane executionLane,
            Function<ArtifactDescriptor, CachedArtifact> materializer) {
        Map<ArtifactDescriptor, ArtifactDescriptor> uniqueDescriptors = new LinkedHashMap<>();
        descriptors.stream()
                .sorted(Comparator.comparing(ArtifactBatchMaterializer::artifactDescriptorKey))
                .forEach(descriptor -> uniqueDescriptors.putIfAbsent(descriptor, descriptor));
        if (uniqueDescriptors.isEmpty()) {
            return Map.of();
        }
        try (ExecutorService executor = executionLane.openExecutor(concurrency)) {
            Map<ArtifactDescriptor, Future<CachedArtifact>> futures = new LinkedHashMap<>();
            for (ArtifactDescriptor descriptor : uniqueDescriptors.values()) {
                futures.put(descriptor, executor.submit(() -> materializer.apply(descriptor)));
            }

            Map<ArtifactDescriptor, CachedArtifact> artifacts = new LinkedHashMap<>();
            List<ArtifactDownloadFailure> failures = new ArrayList<>();
            for (Map.Entry<ArtifactDescriptor, Future<CachedArtifact>> entry : futures.entrySet()) {
                try {
                    artifacts.put(entry.getKey(), entry.getValue().get());
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    failures.add(new ArtifactDownloadFailure(
                            artifactDescriptorKey(entry.getKey()),
                            "interrupted while materializing artifact"));
                } catch (ExecutionException exception) {
                    failures.add(new ArtifactDownloadFailure(
                            artifactDescriptorKey(entry.getKey()),
                            failureMessage(exception.getCause())));
                }
            }
            if (!failures.isEmpty()) {
                throw artifactDownloadException(failures);
            }
            return artifacts;
        }
    }

    private static String artifactDescriptorKey(ArtifactDescriptor descriptor) {
        return descriptor.coordinate()
                + ":"
                + descriptor.classifier().orElse("")
                + ":"
                + descriptor.extension();
    }

    private static ResolveException artifactDownloadException(List<ArtifactDownloadFailure> failures) {
        List<ArtifactDownloadFailure> sorted = failures.stream()
                .sorted(Comparator.comparing(ArtifactDownloadFailure::artifactKey))
                .toList();
        StringBuilder message = new StringBuilder("Selected artifact downloads failed:");
        for (ArtifactDownloadFailure failure : sorted) {
            message.append(System.lineSeparator())
                    .append("- ")
                    .append(failure.artifactKey())
                    .append(": ")
                    .append(failure.message());
        }
        message.append(System.lineSeparator())
                .append("Retry the command or check your repository and network settings.");
        return new ResolveException(message.toString());
    }

    private static String failureMessage(Throwable cause) {
        if (cause == null || cause.getMessage() == null || cause.getMessage().isBlank()) {
            return cause == null ? "download failed" : cause.getClass().getSimpleName();
        }
        return cause.getMessage();
    }

    private record ArtifactDownloadFailure(String artifactKey, String message) {
    }
}
