package sh.zolt.cli.command.quality;

import sh.zolt.cli.CommandHumanOutput;
import sh.zolt.cli.command.CommandFailures;
import sh.zolt.cli.command.CommandProjectDirectory;
import sh.zolt.doctor.JdkDetector;
import sh.zolt.doctor.JdkStatus;
import sh.zolt.doctor.SelfHostingCheckResult;
import sh.zolt.doctor.SelfHostingCheckService;
import sh.zolt.project.ProjectConfig;
import sh.zolt.toml.ZoltConfigException;
import sh.zolt.toml.ZoltTomlParser;
import java.nio.file.Path;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;
import picocli.CommandLine;

@Command(name = "doctor", description = "Inspect local Java/JDK/Zolt project health.")
public final class DoctorCommand implements Runnable {
    private final ZoltTomlParser tomlParser;
    private final JdkDetector jdkDetector;
    private final SelfHostingCheckService selfHostingCheckService;

    @Option(names = "--self-hosting", description = "Check whether the project is ready for Zolt-owned self-hosting flows.")
    private boolean selfHosting;

    @Mixin
    private CommandProjectDirectory projectDirectory = new CommandProjectDirectory();

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
            Path projectRoot = projectDirectory.path();
            ProjectConfig config = tomlParser.parse(projectRoot.resolve("zolt.toml"));
            JdkStatus status = jdkDetector.detect(config.project().java());
            printJdkStatus(status);
            boolean ok = status.ok();
            if (selfHosting) {
                SelfHostingCheckResult result = selfHostingCheckService.check(projectRoot);
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
        CommandHumanOutput output = CommandHumanOutput.of(spec);
        if (status.ok()) {
            output.status("JDK", "ok");
            return;
        }
        output.status("JDK status", "error");
        output.context("JAVA_HOME", status.javaHome().map(Path::toString).orElse("not set"));
        output.context("java", status.java().map(Path::toString).orElse("missing"));
        output.context("javac", status.javac().map(Path::toString).orElse("missing"));
        output.context("jar", status.jar().map(Path::toString).orElse("missing"));
        output.context("version", status.version().orElse("unknown"));
        CommandHumanOutput errors = CommandHumanOutput.errors(spec);
        for (String problem : status.problems()) {
            errors.error(problem);
        }
    }

    private void printSelfHostingStatus(SelfHostingCheckResult result) {
        CommandHumanOutput output = CommandHumanOutput.of(spec);
        if (result.ok()) {
            output.status("Self-hosting", "ok");
            return;
        }
        output.status("Self-hosting status", "error");
        for (SelfHostingCheckResult.SelfHostingCheck check : result.checks()) {
            String marker = check.ok() ? "ok" : "error";
            output.statusDetail(marker, check.name() + " - " + check.message());
        }
    }
}
