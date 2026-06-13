package com.zolt.framework;

import java.util.Optional;

@FunctionalInterface
public interface FrameworkTestRunner {
    Optional<FrameworkTestRunResult> runIfEnabled(FrameworkTestRunRequest request);

    static FrameworkTestRunner none() {
        return request -> Optional.empty();
    }
}
