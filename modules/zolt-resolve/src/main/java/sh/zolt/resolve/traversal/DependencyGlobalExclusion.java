package sh.zolt.resolve.traversal;

import sh.zolt.resolve.request.DependencyExclusion;
import java.util.Optional;

record DependencyGlobalExclusion(
        DependencyExclusion exclusion,
        Optional<String> reason) {
    DependencyGlobalExclusion {
        reason = reason == null ? Optional.empty() : reason;
    }
}
