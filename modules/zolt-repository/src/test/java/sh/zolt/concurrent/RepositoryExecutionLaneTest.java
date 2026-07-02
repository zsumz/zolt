package sh.zolt.concurrent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

final class RepositoryExecutionLaneTest {
    @Test
    void defaultsToPlatformLane() {
        assertEquals(RepositoryExecutionLane.PLATFORM, RepositoryExecutionLane.fromEnvironment(Map.of()));
        assertEquals(RepositoryExecutionLane.PLATFORM, RepositoryExecutionLane.fromIdOrDefault(""));
        assertEquals(RepositoryExecutionLane.PLATFORM, RepositoryExecutionLane.fromIdOrDefault("not-a-lane"));
    }

    @Test
    void parsesVirtualLaneCaseInsensitively() {
        assertEquals(
                RepositoryExecutionLane.VIRTUAL,
                RepositoryExecutionLane.fromEnvironment(Map.of(
                        RepositoryExecutionLane.ENVIRONMENT_KEY,
                        " Virtual ")));
    }

    @Test
    void platformLaneUsesPlatformThreads() throws Exception {
        try (ExecutorService executor = RepositoryExecutionLane.PLATFORM.openExecutor(1)) {
            Future<Boolean> result = executor.submit(() -> Thread.currentThread().isVirtual());

            assertFalse(result.get());
        }
    }

    @Test
    void virtualLaneUsesVirtualThreads() throws Exception {
        try (ExecutorService executor = RepositoryExecutionLane.VIRTUAL.openExecutor(1)) {
            Future<Boolean> result = executor.submit(() -> Thread.currentThread().isVirtual());

            assertTrue(result.get());
        }
    }
}
