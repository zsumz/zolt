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
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

@Command(name = "policy", description = "Show dependency baseline and policy diagnostics.")
public final class PolicyCommand implements Runnable {
    private final ZoltTomlParser tomlParser;
    private final ZoltLockfileReader lockfileReader;
    private final DependencyPolicyReportService reportService;
    private final DependencyPolicyReportFormatter reportFormatter;

    enum Format {
        TEXT,
        JSON
    }

    @Mixin
    private CommandProjectDirectory projectDirectory = new CommandProjectDirectory();

    @Option(names = "--format", description = "Output format: text or json.")
    private Format format = Format.TEXT;

    @Spec
    private CommandSpec spec;

    public PolicyCommand() {
        this(
                new ZoltTomlParser(),
                new ZoltLockfileReader(),
                new DependencyPolicyReportService(),
                new DependencyPolicyReportFormatter());
    }

    PolicyCommand(
            ZoltTomlParser tomlParser,
            ZoltLockfileReader lockfileReader,
            DependencyPolicyReportService reportService,
            DependencyPolicyReportFormatter reportFormatter) {
        this.tomlParser = tomlParser;
        this.lockfileReader = lockfileReader;
        this.reportService = reportService;
        this.reportFormatter = reportFormatter;
    }

    @Override
    public void run() {
        try {
            Path projectRoot = projectDirectory.path();
            ProjectConfig config = tomlParser.parse(projectRoot.resolve("zolt.toml"));
            ZoltLockfile lockfile = lockfileReader.read(projectRoot.resolve("zolt.lock"));
            DependencyPolicyReport report = reportService.report(
                    projectRoot,
                    config,
                    lockfile);
            CommandOutput.printAndFlush(
                    spec,
                    format == Format.JSON ? reportFormatter.json(report) : reportFormatter.text(report));
        } catch (DependencyPolicyReportException | LockfileReadException | ZoltConfigException exception) {
            throw CommandFailures.user(spec, exception);
        }
    }
}
