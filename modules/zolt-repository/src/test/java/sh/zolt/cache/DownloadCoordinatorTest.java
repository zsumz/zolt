package sh.zolt.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import sh.zolt.concurrent.RepositoryExecutionLane;
import sh.zolt.cache.DownloadCoordinator.DownloadCoordinatorException;
import sh.zolt.cache.DownloadCoordinator.DownloadTask;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class DownloadCoordinatorTest {
    @Test
    void duplicateInFlightRequestsRunOnce() throws Exception {
        CountDownLatch duplicateJoined = new CountDownLatch(1);
        DownloadCoordinator coordinator = new DownloadCoordinator(
                4,
                RepositoryExecutionLane.DEFAULT,
                duplicateJoined::countDown);
        AtomicInteger runs = new AtomicInteger();
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<String> first = executor.submit(() -> coordinator.run("repo/path.pom", () -> {
                runs.incrementAndGet();
                firstStarted.countDown();
                await(release);
                return "downloaded";
            }));
            assertTrue(firstStarted.await(2, TimeUnit.SECONDS));

            Future<String> second = executor.submit(() -> coordinator.run("repo/path.pom", () -> {
                runs.incrementAndGet();
                return "duplicate";
            }));
            assertTrue(duplicateJoined.await(2, TimeUnit.SECONDS));
            release.countDown();

            assertEquals(1, runs.get());
            assertEquals("downloaded", first.get());
            assertEquals("downloaded", second.get());
            assertEquals(1, runs.get());
        }
    }

    @Test
    void concurrencyIsBounded() throws Exception {
        DownloadCoordinator coordinator = new DownloadCoordinator(2);
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maxActive = new AtomicInteger();
        CountDownLatch twoActive = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(4)) {
            List<Future<String>> futures = List.of(
                    submitBounded(executor, coordinator, "repo/a.pom", active, maxActive, twoActive, release),
                    submitBounded(executor, coordinator, "repo/b.pom", active, maxActive, twoActive, release),
                    submitBounded(executor, coordinator, "repo/c.pom", active, maxActive, twoActive, release),
                    submitBounded(executor, coordinator, "repo/d.pom", active, maxActive, twoActive, release));

            assertTrue(twoActive.await(2, TimeUnit.SECONDS));
            assertEquals(2, maxActive.get());
            release.countDown();
            for (Future<String> future : futures) {
                assertTrue(future.get().startsWith("repo/"));
            }
            assertTrue(maxActive.get() <= 2);
        }
    }

    @Test
    void concurrencyCanBeConfiguredFromEnvironment() {
        assertEquals(8, DownloadCoordinator.DEFAULT_CONCURRENCY);
        assertEquals(1, DownloadCoordinator.concurrencyFromEnvironment(Map.of("ZOLT_DOWNLOAD_CONCURRENCY", "1")));
        assertEquals(8, DownloadCoordinator.concurrencyFromEnvironment(Map.of("ZOLT_DOWNLOAD_CONCURRENCY", "8")));
        assertEquals(1, DownloadCoordinator.concurrencyFromEnvironment(Map.of("ZOLT_DOWNLOAD_CONCURRENCY", "0")));
        assertEquals(
                DownloadCoordinator.DEFAULT_CONCURRENCY,
                DownloadCoordinator.concurrencyFromEnvironment(Map.of("ZOLT_DOWNLOAD_CONCURRENCY", "   ")));
        assertEquals(DownloadCoordinator.DEFAULT_CONCURRENCY, DownloadCoordinator.concurrencyFromEnvironment(Map.of()));
        assertEquals(
                DownloadCoordinator.DEFAULT_CONCURRENCY,
                DownloadCoordinator.concurrencyFromEnvironment(Map.of("ZOLT_DOWNLOAD_CONCURRENCY", "not-a-number")));
    }

    @Test
    void defaultExecutionLaneIsPlatformThreads() {
        DownloadCoordinator coordinator = new DownloadCoordinator(DownloadCoordinator.DEFAULT_CONCURRENCY);

        assertEquals(RepositoryExecutionLane.PLATFORM, coordinator.executionLane());
    }

    @Test
    void rejectsNonPositiveConcurrency() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new DownloadCoordinator(0));

        assertEquals("Download concurrency must be at least 1.", exception.getMessage());
    }

    @Test
    void runAllReturnsEmptyListWithoutOpeningExecutor() {
        DownloadCoordinator coordinator = new DownloadCoordinator(2, RepositoryExecutionLane.VIRTUAL);

        assertEquals(List.of(), coordinator.runAll(List.of()));
    }

    @Test
    void runAllUsesSelectedExecutionLane() {
        DownloadCoordinator coordinator = new DownloadCoordinator(2, RepositoryExecutionLane.VIRTUAL);

        List<Boolean> virtualThreads = coordinator.runAll(List.of(
                new DownloadTask<>("repo/a.jar", () -> Thread.currentThread().isVirtual()),
                new DownloadTask<>("repo/b.jar", () -> Thread.currentThread().isVirtual())));

        assertEquals(List.of(true, true), virtualThreads);
    }

    @Test
    void groupedFailuresAreSortedByRepositoryPath() {
        DownloadCoordinator coordinator = new DownloadCoordinator(3);

        DownloadCoordinatorException exception = assertThrows(
                DownloadCoordinatorException.class,
                () -> coordinator.runAll(List.of(
                        new DownloadTask<>("repo/z.jar", () -> {
                            throw new ArtifactCacheException("z failed");
                        }),
                        new DownloadTask<>("repo/a.jar", () -> {
                            throw new ArtifactCacheException("a failed");
                        }),
                        new DownloadTask<>("repo/m.jar", () -> "ok"))));

        assertEquals(List.of("repo/a.jar", "repo/z.jar"), exception.failures().stream()
                .map(DownloadCoordinator.DownloadFailure::repositoryPath)
                .toList());
        assertTrue(exception.getMessage().indexOf("repo/a.jar") < exception.getMessage().indexOf("repo/z.jar"));
    }

    @Test
    void groupedFailuresUseDeterministicFallbackMessages() {
        DownloadCoordinator coordinator = new DownloadCoordinator(3);

        DownloadCoordinatorException exception = assertThrows(
                DownloadCoordinatorException.class,
                () -> coordinator.runAll(List.of(
                        new DownloadTask<>("repo/null-message.jar", () -> {
                            throw new RuntimeException();
                        }),
                        new DownloadTask<>("repo/blank-message.jar", () -> {
                            throw new RuntimeException("   ");
                        }),
                        new DownloadTask<>("repo/error.jar", () -> {
                            throw new AssertionError();
                        }))));

        assertEquals(List.of(
                new DownloadCoordinator.DownloadFailure("repo/blank-message.jar", "RuntimeException"),
                new DownloadCoordinator.DownloadFailure("repo/error.jar", "AssertionError"),
                new DownloadCoordinator.DownloadFailure("repo/null-message.jar", "RuntimeException")),
                exception.failures());
        assertTrue(exception.getMessage().contains("Retry the command or check your repository and network settings."));
    }

    @Test
    void runAllInterruptedWaitReportsActionableFailuresAndPreservesInterruptFlag() {
        clearInterruptFlag();
        DownloadCoordinator coordinator = new DownloadCoordinator(1);

        try {
            Thread.currentThread().interrupt();
            DownloadCoordinatorException exception = assertThrows(
                    DownloadCoordinatorException.class,
                    () -> coordinator.runAll(List.of(
                            new DownloadTask<>("repo/a.jar", () -> "a"))));

            assertTrue(Thread.currentThread().isInterrupted());
            assertEquals(List.of(
                    new DownloadCoordinator.DownloadFailure(
                            "repo/a.jar",
                            "interrupted while waiting for download")),
                    exception.failures());
            assertTrue(exception.getMessage().contains("Retry the command or check your repository and network settings."));
        } finally {
            clearInterruptFlag();
        }
    }

    @Test
    void duplicateInFlightRuntimeFailureIsPropagatedToWaiter() throws Exception {
        CountDownLatch duplicateJoined = new CountDownLatch(1);
        DownloadCoordinator coordinator = new DownloadCoordinator(
                2,
                RepositoryExecutionLane.DEFAULT,
                duplicateJoined::countDown);
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseFailure = new CountDownLatch(1);
        ArtifactCacheException failure = new ArtifactCacheException("network failed");

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<String> first = executor.submit(() -> coordinator.run("repo/shared.jar", () -> {
                firstStarted.countDown();
                await(releaseFailure);
                throw failure;
            }));
            assertTrue(firstStarted.await(2, TimeUnit.SECONDS));

            Future<String> second = executor.submit(() -> coordinator.run("repo/shared.jar", () -> "duplicate"));
            assertTrue(duplicateJoined.await(2, TimeUnit.SECONDS));
            releaseFailure.countDown();

            assertSame(failure, executionCause(first));
            assertSame(failure, executionCause(second));
        }
    }

    @Test
    void duplicateInFlightErrorIsPropagatedToWaiter() throws Exception {
        CountDownLatch duplicateJoined = new CountDownLatch(1);
        DownloadCoordinator coordinator = new DownloadCoordinator(
                2,
                RepositoryExecutionLane.DEFAULT,
                duplicateJoined::countDown);
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseFailure = new CountDownLatch(1);
        AssertionError failure = new AssertionError("boom");

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<String> first = executor.submit(() -> coordinator.run("repo/shared.jar", () -> {
                firstStarted.countDown();
                await(releaseFailure);
                throw failure;
            }));
            assertTrue(firstStarted.await(2, TimeUnit.SECONDS));

            Future<String> second = executor.submit(() -> coordinator.run("repo/shared.jar", () -> "duplicate"));
            assertTrue(duplicateJoined.await(2, TimeUnit.SECONDS));
            releaseFailure.countDown();

            assertSame(failure, executionCause(first));
            assertSame(failure, executionCause(second));
        }
    }

    @Test
    void interruptedBeforePermitAcquireFailsClearlyAndPreservesInterruptFlag() {
        clearInterruptFlag();
        DownloadCoordinator coordinator = new DownloadCoordinator(1);

        try {
            Thread.currentThread().interrupt();
            ArtifactCacheException exception = assertThrows(
                    ArtifactCacheException.class,
                    () -> coordinator.run("repo/interrupted.jar", () -> {
                        throw new AssertionError("interrupted run should not start the download");
                    }));

            assertTrue(Thread.currentThread().isInterrupted());
            assertTrue(exception.getMessage().contains("Download interrupted while waiting to fetch repo/interrupted.jar"));
            assertTrue(exception.getMessage().contains("Try again."));
        } finally {
            clearInterruptFlag();
        }
    }

    @Test
    void interruptedDuplicateWaiterFailsClearlyAndPreservesInterruptFlag() throws Exception {
        clearInterruptFlag();
        DownloadCoordinator coordinator = new DownloadCoordinator(1);
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            Future<String> first = executor.submit(() -> coordinator.run("repo/shared.jar", () -> {
                firstStarted.countDown();
                await(release);
                return "downloaded";
            }));
            assertTrue(firstStarted.await(2, TimeUnit.SECONDS));

            try {
                Thread.currentThread().interrupt();
                ArtifactCacheException exception = assertThrows(
                        ArtifactCacheException.class,
                        () -> coordinator.run("repo/shared.jar", () -> "duplicate"));

                assertTrue(Thread.currentThread().isInterrupted());
                assertTrue(exception.getMessage().contains(
                        "Download interrupted while waiting for in-flight fetch of repo/shared.jar"));
                assertTrue(exception.getMessage().contains("Try again."));
            } finally {
                clearInterruptFlag();
                release.countDown();
            }

            assertEquals("downloaded", first.get());
        }
    }

    private static Future<String> submitBounded(
            ExecutorService executor,
            DownloadCoordinator coordinator,
            String repositoryPath,
            AtomicInteger active,
            AtomicInteger maxActive,
            CountDownLatch twoActive,
            CountDownLatch release) {
        return executor.submit(() -> coordinator.run(repositoryPath, () -> {
            int now = active.incrementAndGet();
            maxActive.accumulateAndGet(now, Math::max);
            if (now == 2) {
                twoActive.countDown();
            }
            await(release);
            active.decrementAndGet();
            return repositoryPath;
        }));
    }

    private static void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(2, TimeUnit.SECONDS));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while waiting for test latch.", exception);
        }
    }

    private static void clearInterruptFlag() {
        Thread.interrupted();
    }

    private static Throwable executionCause(Future<String> future) {
        ExecutionException exception = assertThrows(ExecutionException.class, future::get);
        return exception.getCause();
    }
}
