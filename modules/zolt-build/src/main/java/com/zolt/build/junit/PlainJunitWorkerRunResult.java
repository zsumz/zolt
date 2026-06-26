package com.zolt.build.junit;

import com.zolt.junit.JunitWorkerClient;

public record PlainJunitWorkerRunResult(
        JunitWorkerClient.WorkerRunResult workerResult,
        long startupNanos,
        long requestNanos) {
}
