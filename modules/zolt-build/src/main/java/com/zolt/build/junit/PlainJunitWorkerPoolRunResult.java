package com.zolt.build.junit;

public record PlainJunitWorkerPoolRunResult(
        String output,
        int workerRequests,
        long startupNanos,
        long requestNanos) {
}
