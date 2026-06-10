package com.zolt.junit;

import java.nio.file.Path;

public final class JunitWorkerProcess implements AutoCloseable {
    private final JunitWorkerClient client;
    private final ProcessCloser processCloser;
    private boolean closed;

    JunitWorkerProcess(
            JunitWorkerClient client,
            ProcessCloser processCloser) {
        if (client == null) {
            throw new IllegalArgumentException("JUnit worker client is required.");
        }
        if (processCloser == null) {
            throw new IllegalArgumentException("JUnit worker process closer is required.");
        }
        this.client = client;
        this.processCloser = processCloser;
    }

    public JunitWorkerClient.WorkerRunResult run(Path testOutputDirectory) {
        if (closed) {
            throw new JunitWorkerClientException("JUnit worker process is already closed.");
        }
        return client.run(testOutputDirectory);
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            client.close();
        } finally {
            processCloser.close();
        }
    }

    @FunctionalInterface
    interface ProcessCloser {
        void close();
    }
}
