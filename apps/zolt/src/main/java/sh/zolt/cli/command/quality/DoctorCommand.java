package sh.zolt.cli.command.quality;

import sh.zolt.cli.CommandHumanOutput;
import sh.zolt.cli.command.CommandFailures;
import sh.zolt.cli.command.CommandProjectDirectory;
import sh.zolt.doctor.JdkDetector;
import sh.zolt.doctor.JdkStatus;
import sh.zolt.doctor.SelfHostingCheckResult;
import sh.zolt.doctor.SelfHostingCheckService;
import sh.zolt.project.ProjectConfig;
import sh.zolt.toolchain.TestRuntimeToolchain;
import sh.zolt.toolchain.TestRuntimeToolchainResolver;
import sh.zolt.toolchain.platform.HostPlatform;
import sh.zolt.toolchain.store.ToolchainStore;
import sh.zolt.toml.ZoltConfigException;
import sh.zolt.toml.ZoltTomlParser;
import java.nio.file.Path;
import java.util.Optional;
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
            Optional<TestRuntimeToolchain> testRuntime = new TestRuntimeToolchainResolver()
                    .resolve(projectRoot, projectRoot, config, HostPlatform.current(), ToolchainStore.defaults());
            if (testRuntime.isPresent()) {
                ok = printTestRuntimeStatus(testRuntime.orElseThrow()) && ok;
            }
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

    private boolean printTestRuntimeStatus(TestRuntimeToolchain testRuntime) {
        CommandHumanOutput output = CommandHumanOutput.of(spec);
        if (testRuntime.ready()) {
            output.status("Test runtime JDK", "ok");
            output.context("version", testRuntime.request().version());
            output.context("java", testRuntime.java().map(Path::toString).orElse("resolved"));
            return true;
        }
        output.status("Test runtime JDK status", "error");
        output.context("requested", testRuntime.request().version());
        CommandHumanOutput errors = CommandHumanOutput.errors(spec);
        testRuntime.problem().ifPresent(errors::error);
        testRuntime.remediation().ifPresent(errors::error);
        return false;
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
