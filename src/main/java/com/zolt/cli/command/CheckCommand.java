package com.zolt.cli.command;

import com.zolt.cache.LocalArtifactCache;
import com.zolt.cli.ZoltCli;
import com.zolt.perf.TimingRecorder;
import com.zolt.quality.QualityCheckContext;
import com.zolt.quality.QualityCheckFormatter;
import com.zolt.quality.QualityCheckReport;
import com.zolt.quality.QualityCheckRequest;
import com.zolt.quality.QualityCheckService;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

@Command(name = "check", description = "Run Zolt-owned quality checks.")
public final class CheckCommand implements Callable<Integer> {
    enum Format {
        TEXT,
        JSON
    }

    @Option(names = "--check", description = "Run a quality check id. May be repeated.")
    private List<String> checks = List.of();

    @Option(names = "--context", description = "Apply a built-in check context. Supported values: local, ci.")
    private QualityCheckContext context;

    @Option(names = "--reports-dir", description = "Validate project-relative JUnit XML report output for CI context.")
    private Path reportsDir;

    @Option(names = "--require-package", description = "Require the configured package artifact and package evidence during CI context checks.")
    private boolean requirePackage;

    @Option(names = "--require-publish-dry-run", description = "Require publish dry-run preflight during CI context checks without uploading.")
    private boolean requirePublishDryRun;

    @Option(names = "--require-offline-ready", description = "Require locked dependency metadata to be available from the local cache during CI context checks.")
    private boolean requireOfflineReady;

    @Option(names = "--workspace", description = "Check workspace members using the workspace selection model.")
    private boolean workspace;

    @Option(names = "--offline", description = "Use only artifacts already present in the local cache for checks that need dependency metadata.")
    private boolean offline;

    @Option(names = "--all", description = "Select every workspace member.")
    private boolean all;

    @Option(names = "--member", description = "Select a workspace member by declared path. May be repeated.")
    private List<String> members = List.of();

    @Option(names = "--members", split = ",", description = "Select comma-separated workspace members by declared path.")
    private List<String> memberGroups = List.of();

    @Option(names = "--format", description = "Output format: text or json.")
    private Format format = Format.TEXT;

    @Option(names = "--cwd", hidden = true)
    private Path workingDirectory = Path.of(".");

    @Option(names = "--cache-root", hidden = true)
    private Path cacheRoot = LocalArtifactCache.defaultRoot();

    @Mixin
    private ZoltCli.TimingOptions timingOptions = new ZoltCli.TimingOptions();

    @Spec
    private CommandSpec spec;

    @Override
    public Integer call() {
        TimingRecorder timings = CommandTimings.recorder(timingOptions);
        QualityCheckReport report = timings.measure(
                "run quality checks",
                () -> new QualityCheckService().check(new QualityCheckRequest(
                        workingDirectory,
                        cacheRoot,
                        offline,
                        workspace,
                        checks,
                        context,
                        reportsDir,
                        requirePackage,
                        requirePublishDryRun,
                        requireOfflineReady,
                        CommandWorkspaceSelections.from(all, members, memberGroups))),
                CheckCommand::qualityCheckAttributes);
        if (format == Format.JSON) {
            CommandOutput.printAndFlush(spec, QualityCheckFormatter.json(report));
        } else {
            CommandOutput.printAndFlush(spec, QualityCheckFormatter.text(report));
        }
        CommandTimings.print(spec, "check", workingDirectory, timingOptions, timings);
        return report.ok() ? 0 : 1;
    }

    private static Map<String, String> qualityCheckAttributes(QualityCheckReport result) {
        Map<String, String> attributes = new LinkedHashMap<>();
        attributes.put("checks", Integer.toString(result.checks().size()));
        attributes.put("passed", Long.toString(result.passedCount()));
        attributes.put("failed", Long.toString(result.failedCount()));
        attributes.put("skipped", Long.toString(result.skippedCount()));
        attributes.put("workspace", Boolean.toString(result.workspace()));
        attributes.put("ok", Boolean.toString(result.ok()));
        return attributes;
    }
}
