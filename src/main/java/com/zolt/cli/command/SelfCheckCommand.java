package com.zolt.cli.command;

import com.zolt.cache.LocalArtifactCache;
import com.zolt.cli.ZoltCli;
import com.zolt.perf.TimingRecorder;
import com.zolt.selfhost.SelfCheckResult;
import com.zolt.selfhost.SelfCheckService;
import java.nio.file.Path;
import java.util.Map;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

@Command(name = "self-check", description = "Run the self-hosting verification path.")
public final class SelfCheckCommand implements Runnable {
    @Option(names = "--offline", description = "Use only artifacts already present in the local cache.")
    private boolean offline;

    @Option(names = "--native", description = "Also build and smoke the Native Image binary.")
    private boolean nativeCheck;

    @Option(names = "--native-image", description = "Path to the native-image executable.")
    private Path nativeImageExecutable;

    @Option(names = "--cwd", hidden = true)
    private Path workingDirectory = Path.of(".");

    @Option(names = "--cache-root", hidden = true)
    private Path cacheRoot = LocalArtifactCache.defaultRoot();

    @Mixin
    private ZoltCli.TimingOptions timingOptions = new ZoltCli.TimingOptions();

    @Spec
    private CommandSpec spec;

    @Override
    public void run() {
        TimingRecorder timings = CommandTimings.recorder(timingOptions);
        try {
            SelfCheckResult result = timings.measure(
                    "self-check",
                    () -> new SelfCheckService().check(
                            workingDirectory,
                            cacheRoot,
                            offline,
                            nativeCheck,
                            nativeImageExecutable),
                    SelfCheckCommand::selfCheckAttributes);
            printSelfCheckStatus(result);
            if (!result.ok()) {
                throw new CommandLine.ExecutionException(spec.commandLine(), "Self-check failed.");
            }
        } finally {
            CommandTimings.print(spec, "self-check", workingDirectory, timingOptions, timings);
        }
    }

    private static Map<String, String> selfCheckAttributes(SelfCheckResult result) {
        long failedSteps = result.steps().stream().filter(step -> !step.ok()).count();
        return Map.of(
                "steps", Integer.toString(result.steps().size()),
                "failedSteps", Long.toString(failedSteps),
                "ok", Boolean.toString(result.ok()));
    }

    private void printSelfCheckStatus(SelfCheckResult result) {
        spec.commandLine().getOut().println("Self-check status: " + (result.ok() ? "ok" : "error"));
        for (SelfCheckResult.SelfCheckStep step : result.steps()) {
            String marker = step.ok() ? "ok" : "error";
            spec.commandLine().getOut().println(marker + ": " + step.name() + " - " + step.message());
        }
    }
}
