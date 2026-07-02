package sh.zolt.test.shard;

import java.util.List;

public record TestWorkerPoolPlan(
        boolean enabled,
        int maxWorkers,
        List<TestWorkerPoolWave> waves) {
    public TestWorkerPoolPlan {
        if (maxWorkers < 1) {
            throw new IllegalArgumentException("Test worker pool maxWorkers must be greater than zero.");
        }
        waves = List.copyOf(waves == null ? List.of() : waves);
    }

    public boolean empty() {
        return waves.isEmpty() || waves.stream().allMatch(wave -> wave.entries().isEmpty());
    }
}
