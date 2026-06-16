package com.zolt.resolve;

import java.util.Optional;

record DependencyGlobalExclusion(
        DependencyExclusion exclusion,
        Optional<String> reason) {
    DependencyGlobalExclusion {
        reason = reason == null ? Optional.empty() : reason;
    }
}
