package com.zolt.build;

import com.zolt.junit.JunitWorkerClient;

record PlainJunitWorkerRunResult(
        JunitWorkerClient.WorkerRunResult workerResult,
        long startupNanos,
        long requestNanos) {
}
