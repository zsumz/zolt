package sh.zolt.build.junit;

import sh.zolt.junit.JunitWorkerClient;

public record PlainJunitWorkerRunResult(
        JunitWorkerClient.WorkerRunResult workerResult,
        long startupNanos,
        long requestNanos) {
}
