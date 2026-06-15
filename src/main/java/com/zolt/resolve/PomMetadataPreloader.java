package com.zolt.resolve;

import com.zolt.concurrent.RepositoryExecutionLane;
import com.zolt.maven.Coordinate;
import com.zolt.maven.EffectiveRawPom;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.Function;

final class PomMetadataPreloader {
    void preload(
            List<Coordinate> coordinates,
            int concurrency,
            Function<Coordinate, EffectiveRawPom> loader) {
        preload(coordinates, concurrency, RepositoryExecutionLane.DEFAULT, loader);
    }

    void preload(
            List<Coordinate> coordinates,
            int concurrency,
            RepositoryExecutionLane executionLane,
            Function<Coordinate, EffectiveRawPom> loader) {
        Map<String, Coordinate> uniqueCoordinates = new LinkedHashMap<>();
        coordinates.stream()
                .sorted(Comparator.comparing(Coordinate::toString))
                .forEach(coordinate -> uniqueCoordinates.putIfAbsent(coordinate.toString(), coordinate));
        if (uniqueCoordinates.isEmpty()) {
            return;
        }
        try (ExecutorService executor = executionLane.openExecutor(concurrency)) {
            Map<String, Future<EffectiveRawPom>> futures = new LinkedHashMap<>();
            for (Map.Entry<String, Coordinate> entry : uniqueCoordinates.entrySet()) {
                futures.put(entry.getKey(), executor.submit(() -> loader.apply(entry.getValue())));
            }

            List<PomMetadataFailure> failures = new ArrayList<>();
            for (Map.Entry<String, Future<EffectiveRawPom>> entry : futures.entrySet()) {
                try {
                    entry.getValue().get();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    failures.add(new PomMetadataFailure(
                            entry.getKey(),
                            "interrupted while fetching POM metadata"));
                } catch (ExecutionException exception) {
                    failures.add(new PomMetadataFailure(entry.getKey(), failureMessage(exception.getCause())));
                }
            }
            if (!failures.isEmpty()) {
                throw pomMetadataException(failures);
            }
        }
    }

    private static ResolveException pomMetadataException(List<PomMetadataFailure> failures) {
        List<PomMetadataFailure> sorted = failures.stream()
                .sorted(Comparator.comparing(PomMetadataFailure::coordinate))
                .toList();
        StringBuilder message = new StringBuilder("POM metadata fetch failed:");
        for (PomMetadataFailure failure : sorted) {
            message.append(System.lineSeparator())
                    .append("- ")
                    .append(failure.coordinate())
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

    private record PomMetadataFailure(String coordinate, String message) {
    }
}
