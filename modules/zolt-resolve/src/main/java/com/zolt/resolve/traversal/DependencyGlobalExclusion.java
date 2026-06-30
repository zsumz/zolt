package com.zolt.resolve.traversal;

import com.zolt.resolve.request.DependencyExclusion;
import java.util.Optional;

record DependencyGlobalExclusion(
        DependencyExclusion exclusion,
        Optional<String> reason) {
    DependencyGlobalExclusion {
        reason = reason == null ? Optional.empty() : reason;
    }
}
