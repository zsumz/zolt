package com.zolt.resolve.progress;

import com.zolt.maven.ArtifactDescriptor;

/**
 * Receives artifact download lifecycle events from the resolver without coupling resolver or
 * repository code to the CLI.
 *
 * <p>Callbacks may run on repository worker threads. Implementations that collect state or render
 * output must be thread-safe. Cached, offline, and local-overlay artifacts do not emit download
 * lifecycle events.
 */
public interface ArtifactProgressListener {
    ArtifactProgressListener NOOP = new ArtifactProgressListener() {
    };

    default void onStart(ArtifactDescriptor descriptor) {
    }

    default void onComplete(ArtifactDescriptor descriptor, long bytes) {
    }

    default void onFailure(ArtifactDescriptor descriptor, Throwable failure) {
    }
}
