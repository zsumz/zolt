package com.zolt.resolve;

import com.zolt.lockfile.ZoltLockfile;

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
