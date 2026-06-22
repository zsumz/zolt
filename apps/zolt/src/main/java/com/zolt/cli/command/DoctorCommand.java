package com.zolt.cli.command;

import com.zolt.doctor.JdkDetector;
import com.zolt.doctor.JdkStatus;
import com.zolt.doctor.SelfHostingCheckResult;
import com.zolt.doctor.SelfHostingCheckService;
import com.zolt.project.ProjectConfig;
import com.zolt.toml.ZoltConfigException;
import com.zolt.toml.ZoltTomlParser;
import java.nio.file.Path;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

@Command(name = "doctor", description = "Inspect local Java/JDK/Zolt project health.")
public final class DoctorCommand implements Runnable {
    private final ZoltTomlParser tomlParser;
    private final JdkDetector jdkDetector;
    private final SelfHostingCheckService selfHostingCheckService;

    @Option(names = "--self-hosting", description = "Check whether the project is ready for Zolt-owned self-hosting flows.")
    private boolean selfHosting;

    @Option(names = "--cwd", hidden = true)
    private Path workingDirectory = Path.of(".");

    @Spec
    private CommandSpec spec;

    public DoctorCommand() {
        this(new ZoltTomlParser(), new JdkDetector(), new SelfHostingCheckService());
    }

    DoctorCommand(
            ZoltTomlParser tomlParser,
            JdkDetector jdkDetector,
            SelfHostingCheckService selfHostingCheckService) {
        this.tomlParser = tomlParser;
        this.jdkDetector = jdkDetector;
        this.selfHostingCheckService = selfHostingCheckService;
    }

    @Override
    public void run() {
        try {
            ProjectConfig config = tomlParser.parse(workingDirectory.resolve("zolt.toml"));
            JdkStatus status = jdkDetector.detect(config.project().java());
            printJdkStatus(status);
            boolean ok = status.ok();
            if (selfHosting) {
                SelfHostingCheckResult result = selfHostingCheckService.check(workingDirectory);
                printSelfHostingStatus(result);
                ok = ok && result.ok();
            }
            if (!ok) {
                throw new CommandLine.ExecutionException(spec.commandLine(), "Project health check failed.");
            }
        } catch (ZoltConfigException exception) {
            throw CommandFailures.user(spec, exception);
        }
    }

    private void printJdkStatus(JdkStatus status) {
        spec.commandLine().getOut().println("JDK status: " + (status.ok() ? "ok" : "error"));
        spec.commandLine().getOut().println("JAVA_HOME: " + status.javaHome().map(Path::toString).orElse("not set"));
        spec.commandLine().getOut().println("java: " + status.java().map(Path::toString).orElse("missing"));
        spec.commandLine().getOut().println("javac: " + status.javac().map(Path::toString).orElse("missing"));
        spec.commandLine().getOut().println("jar: " + status.jar().map(Path::toString).orElse("missing"));
        spec.commandLine().getOut().println("version: " + status.version().orElse("unknown"));
        for (String problem : status.problems()) {
            spec.commandLine().getErr().println("error: " + problem);
        }
    }

    private void printSelfHostingStatus(SelfHostingCheckResult result) {
        spec.commandLine().getOut().println("Self-hosting status: " + (result.ok() ? "ok" : "error"));
        for (SelfHostingCheckResult.SelfHostingCheck check : result.checks()) {
            String marker = check.ok() ? "ok" : "error";
            spec.commandLine().getOut().println(marker + ": " + check.name() + " - " + check.message());
        }
    }
}
