package com.zolt.quality;

import java.nio.file.Path;
import java.util.List;

public record QualityCheckReport(
        Path projectRoot,
        boolean workspace,
        List<QualityCheckResult> checks) {
    public QualityCheckReport {
        projectRoot = projectRoot.toAbsolutePath().normalize();
        checks = List.copyOf(checks);
    }

    public boolean ok() {
        return checks.stream().noneMatch(check -> check.status() == QualityCheckStatus.FAILED);
    }

    public String status() {
        return ok() ? "ok" : "error";
    }

    public long passedCount() {
        return checks.stream().filter(check -> check.status() == QualityCheckStatus.PASSED).count();
    }

    public long failedCount() {
        return checks.stream().filter(check -> check.status() == QualityCheckStatus.FAILED).count();
    }

    public long skippedCount() {
        return checks.stream().filter(check -> check.status() == QualityCheckStatus.SKIPPED).count();
    }
}
