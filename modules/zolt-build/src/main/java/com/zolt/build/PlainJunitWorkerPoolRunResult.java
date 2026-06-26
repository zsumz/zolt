package com.zolt.build;

record PlainJunitWorkerPoolRunResult(
        String output,
        int workerRequests,
        long startupNanos,
        long requestNanos) {
}
