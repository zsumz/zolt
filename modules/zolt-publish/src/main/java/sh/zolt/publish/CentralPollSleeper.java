package sh.zolt.publish;

import java.time.Duration;

/**
 * Spaces successive Central Portal status polls. The production implementation sleeps the calling
 * thread; tests substitute a no-op that may instead advance a fake {@link java.time.Clock}, so the
 * poll loop runs deterministically without real delays.
 */
@FunctionalInterface
public interface CentralPollSleeper {
    void sleep(Duration duration) throws InterruptedException;

    static CentralPollSleeper realTime() {
        return duration -> Thread.sleep(Math.max(0L, duration.toMillis()));
    }
}
