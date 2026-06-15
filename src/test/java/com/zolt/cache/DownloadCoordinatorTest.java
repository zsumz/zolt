package com.zolt.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.concurrent.RepositoryExecutionLane;
import com.zolt.cache.DownloadCoordinator.DownloadCoordinatorException;
import com.zolt.cache.DownloadCoordinator.DownloadTask;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class DownloadCoordinatorTest {
    @Test
    void duplicateInFlightRequestsRunOnce() throws Exception {
        DownloadCoordinator coordinator = new DownloadCoordinator(4);
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

            executor.submit(() -> {
                sleepBriefly();
                release.countDown();
            });
            String second = coordinator.run("repo/path.pom", () -> {
                runs.incrementAndGet();
                return "duplicate";
            });

            assertEquals(1, runs.get());
            assertEquals("downloaded", first.get());
            assertEquals("downloaded", second);
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
        assertEquals(1, DownloadCoordinator.concurrencyFromEnvironment(Map.of("ZOLT_DOWNLOAD_CONCURRENCY", "1")));
        assertEquals(8, DownloadCoordinator.concurrencyFromEnvironment(Map.of("ZOLT_DOWNLOAD_CONCURRENCY", "8")));
        assertEquals(1, DownloadCoordinator.concurrencyFromEnvironment(Map.of("ZOLT_DOWNLOAD_CONCURRENCY", "0")));
        assertEquals(DownloadCoordinator.DEFAULT_CONCURRENCY, DownloadCoordinator.concurrencyFromEnvironment(Map.of()));
        assertEquals(
                DownloadCoordinator.DEFAULT_CONCURRENCY,
                DownloadCoordinator.concurrencyFromEnvironment(Map.of("ZOLT_DOWNLOAD_CONCURRENCY", "not-a-number")));
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

    private static void sleepBriefly() {
        try {
            Thread.sleep(50L);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while sleeping in test.", exception);
        }
    }
}
