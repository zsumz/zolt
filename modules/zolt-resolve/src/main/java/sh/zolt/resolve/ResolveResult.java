package sh.zolt.resolve;

import sh.zolt.resolve.metrics.ResolveMetrics;
import java.nio.file.Path;

public record ResolveResult(
        int resolvedCount,
        int downloadCount,
        int conflictCount,
        Path lockfilePath,
        ResolveMetrics metrics) {
    public ResolveResult(
            int resolvedCount,
            int downloadCount,
            int conflictCount,
            Path lockfilePath) {
        this(resolvedCount, downloadCount, conflictCount, lockfilePath, ResolveMetrics.empty());
    }

    public ResolveResult {
        metrics = metrics == null ? ResolveMetrics.empty() : metrics;
    }
}
