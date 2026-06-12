package com.zolt.cli.command;

import com.zolt.lockfile.LockfileReadException;
import com.zolt.lockfile.ZoltLockfile;
import com.zolt.lockfile.ZoltLockfileReader;
import com.zolt.policy.DependencyPolicyReport;
import com.zolt.policy.DependencyPolicyReportException;
import com.zolt.policy.DependencyPolicyReportFormatter;
import com.zolt.policy.DependencyPolicyReportService;
import com.zolt.project.ProjectConfig;
import com.zolt.toml.ZoltConfigException;
import com.zolt.toml.ZoltTomlParser;
import java.nio.file.Path;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

@Command(name = "policy", description = "Show dependency baseline and policy diagnostics.")
public final class PolicyCommand implements Runnable {
    enum Format {
        TEXT,
        JSON
    }

    @Option(names = "--cwd", hidden = true)
    private Path workingDirectory = Path.of(".");

    @Option(names = "--format", description = "Output format: text or json.")
    private Format format = Format.TEXT;

    @Spec
    private CommandSpec spec;

    @Override
    public void run() {
        try {
            ProjectConfig config = new ZoltTomlParser().parse(workingDirectory.resolve("zolt.toml"));
            ZoltLockfile lockfile = new ZoltLockfileReader().read(workingDirectory.resolve("zolt.lock"));
            DependencyPolicyReport report = new DependencyPolicyReportService().report(
                    workingDirectory,
                    config,
                    lockfile);
            DependencyPolicyReportFormatter formatter = new DependencyPolicyReportFormatter();
            CommandOutput.printAndFlush(spec, format == Format.JSON ? formatter.json(report) : formatter.text(report));
        } catch (DependencyPolicyReportException | LockfileReadException | ZoltConfigException exception) {
            spec.commandLine().getErr().println("error: " + exception.getMessage());
            throw new CommandLine.ExecutionException(spec.commandLine(), exception.getMessage(), exception);
        }
    }
}
