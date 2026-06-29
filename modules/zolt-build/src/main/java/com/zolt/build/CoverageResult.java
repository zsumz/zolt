package com.zolt.build;

import com.zolt.build.testruntime.TestRunResult;
import java.nio.file.Path;
import java.util.Optional;

public record CoverageResult(
        TestRunResult testRunResult,
        String reportOutput,
        Path execFile,
        Optional<Path> xmlReport,
        Optional<Path> htmlDirectory) {
    public CoverageResult {
        xmlReport = xmlReport == null ? Optional.empty() : xmlReport;
        htmlDirectory = htmlDirectory == null ? Optional.empty() : htmlDirectory;
    }
}
