package sh.zolt.resolve;

import sh.zolt.lockfile.ZoltLockfile;
import sh.zolt.resolve.metrics.ResolveMetrics;

public record ResolveOutput(
        ZoltLockfile lockfile,
        int downloadCount,
        ResolveMetrics metrics) {
    public ResolveOutput(ZoltLockfile lockfile, int downloadCount) {
        this(lockfile, downloadCount, ResolveMetrics.empty());
    }

    public ResolveOutput {
        metrics = metrics == null ? ResolveMetrics.empty() : metrics;
    }
}
