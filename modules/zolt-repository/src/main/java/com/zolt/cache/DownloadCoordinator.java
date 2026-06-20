package com.zolt.cache;

import com.zolt.concurrent.RepositoryExecutionLane;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;

public final class DownloadCoordinator {
    public static final int DEFAULT_CONCURRENCY = 8;
    public static final String CONCURRENCY_ENV = "ZOLT_DOWNLOAD_CONCURRENCY";

    private final int concurrency;
    private final RepositoryExecutionLane executionLane;
    private final Semaphore permits;
    private final ConcurrentHashMap<String, CompletableFuture<Object>> inFlight = new ConcurrentHashMap<>();

    public DownloadCoordinator() {
        this(concurrencyFromEnvironment(System.getenv()), RepositoryExecutionLane.fromEnvironment(System.getenv()));
    }

    public DownloadCoordinator(int concurrency) {
        this(concurrency, RepositoryExecutionLane.DEFAULT);
    }

    public DownloadCoordinator(int concurrency, RepositoryExecutionLane executionLane) {
        if (concurrency < 1) {
            throw new IllegalArgumentException("Download concurrency must be at least 1.");
        }
        this.concurrency = concurrency;
        this.executionLane = Objects.requireNonNull(executionLane, "executionLane");
        this.permits = new Semaphore(concurrency);
    }

    public static int concurrencyFromEnvironment(Map<String, String> environment) {
        String configured = environment.get(CONCURRENCY_ENV);
        if (configured == null || configured.isBlank()) {
            return DEFAULT_CONCURRENCY;
        }
        try {
            int parsed = Integer.parseInt(configured.trim());
            return Math.max(1, parsed);
        } catch (NumberFormatException ignored) {
            return DEFAULT_CONCURRENCY;
        }
    }

    public int concurrency() {
        return concurrency;
    }

    public RepositoryExecutionLane executionLane() {
        return executionLane;
    }

    public <T> T run(String repositoryPath, Supplier<T> action) {
        Objects.requireNonNull(repositoryPath, "repositoryPath");
        Objects.requireNonNull(action, "action");
        CompletableFuture<Object> pending = new CompletableFuture<>();
        CompletableFuture<Object> existing = inFlight.putIfAbsent(repositoryPath, pending);
        if (existing != null) {
            return await(repositoryPath, existing);
        }

        boolean acquired = false;
        try {
            permits.acquire();
            acquired = true;
            T result = action.get();
            pending.complete(result);
            return result;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            ArtifactCacheException wrapped = new ArtifactCacheException(
                    "Download interrupted while waiting to fetch "
                            + repositoryPath
                            + ". Try again.",
                    exception);
            pending.completeExceptionally(wrapped);
            throw wrapped;
        } catch (RuntimeException exception) {
            pending.completeExceptionally(exception);
            throw exception;
        } catch (Error error) {
            pending.completeExceptionally(error);
            throw error;
        } finally {
            if (acquired) {
                permits.release();
            }
            inFlight.remove(repositoryPath, pending);
        }
    }

    public <T> List<T> runAll(List<DownloadTask<T>> tasks) {
        List<DownloadTask<T>> planned = List.copyOf(tasks);
        if (planned.isEmpty()) {
            return List.of();
        }
        try (ExecutorService executor = executionLane.openExecutor(concurrency)) {
            List<CompletableFuture<T>> futures = planned.stream()
                    .map(task -> CompletableFuture.supplyAsync(() -> run(task.repositoryPath(), task.action()), executor))
                    .toList();
            List<T> results = new ArrayList<>(planned.size());
            Map<String, DownloadFailure> failuresByPath = new LinkedHashMap<>();
            for (int index = 0; index < futures.size(); index++) {
                DownloadTask<T> task = planned.get(index);
                try {
                    results.add(futures.get(index).get());
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    failuresByPath.putIfAbsent(
                            task.repositoryPath(),
                            new DownloadFailure(task.repositoryPath(), "interrupted while waiting for download"));
                } catch (ExecutionException exception) {
                    Throwable cause = exception.getCause();
                    failuresByPath.putIfAbsent(task.repositoryPath(), new DownloadFailure(task.repositoryPath(), message(cause)));
                }
            }
            if (!failuresByPath.isEmpty()) {
                throw new DownloadCoordinatorException(failuresByPath.values());
            }
            return List.copyOf(results);
        }
    }

    private static <T> T await(String repositoryPath, CompletableFuture<Object> future) {
        try {
            @SuppressWarnings("unchecked")
            T result = (T) future.get();
            return result;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ArtifactCacheException(
                    "Download interrupted while waiting for in-flight fetch of "
                            + repositoryPath
                            + ". Try again.",
                    exception);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new ArtifactCacheException(
                    "Download failed while waiting for in-flight fetch of "
                            + repositoryPath
                            + ". Try again.",
                    cause);
        }
    }

    private static String message(Throwable cause) {
        if (cause == null || cause.getMessage() == null || cause.getMessage().isBlank()) {
            return cause == null ? "download failed" : cause.getClass().getSimpleName();
        }
        return cause.getMessage();
    }

    public record DownloadTask<T>(String repositoryPath, Supplier<T> action) {
        public DownloadTask {
            Objects.requireNonNull(repositoryPath, "repositoryPath");
            Objects.requireNonNull(action, "action");
        }
    }

    public record DownloadFailure(String repositoryPath, String message) {
        public DownloadFailure {
            Objects.requireNonNull(repositoryPath, "repositoryPath");
            Objects.requireNonNull(message, "message");
        }
    }

    public static final class DownloadCoordinatorException extends RuntimeException {
        private final List<DownloadFailure> failures;

        public DownloadCoordinatorException(Iterable<DownloadFailure> failures) {
            this(sortedFailures(failures));
        }

        private DownloadCoordinatorException(List<DownloadFailure> failures) {
            super(message(failures));
            this.failures = failures;
        }

        public List<DownloadFailure> failures() {
            return failures;
        }

        private static List<DownloadFailure> sortedFailures(Iterable<DownloadFailure> failures) {
            List<DownloadFailure> sorted = new ArrayList<>();
            failures.forEach(sorted::add);
            sorted.sort(Comparator.comparing(DownloadFailure::repositoryPath));
            return List.copyOf(sorted);
        }

        private static String message(List<DownloadFailure> failures) {
            StringBuilder builder = new StringBuilder("Downloads failed:");
            for (DownloadFailure failure : failures) {
                builder.append(System.lineSeparator())
                        .append("- ")
                        .append(failure.repositoryPath())
                        .append(": ")
                        .append(failure.message());
            }
            builder.append(System.lineSeparator())
                    .append("Retry the command or check your repository and network settings.");
            return builder.toString();
        }
    }
}
