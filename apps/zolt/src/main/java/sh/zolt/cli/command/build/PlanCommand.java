package sh.zolt.cli.command.build;

import sh.zolt.build.testruntime.TestReportSettings;
import sh.zolt.test.runtime.TestRunException;
import sh.zolt.cli.command.CommandFailures;
import sh.zolt.cli.command.CommandOutput;
import sh.zolt.cli.command.CommandProjectDirectory;
import sh.zolt.plan.BuildPlan;
import sh.zolt.plan.BuildPlanFormatter;
import sh.zolt.plan.BuildPlanService;
import sh.zolt.plan.PlanTarget;
import sh.zolt.project.ProjectConfig;
import sh.zolt.toml.ZoltConfigException;
import sh.zolt.toml.ZoltTomlParser;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

@Command(name = "plan", description = "Show the typed Zolt command plan without executing it.")
public final class PlanCommand implements Callable<Integer> {
    private final ZoltTomlParser tomlParser;
    private final BuildPlanService buildPlanService;
    private final BuildPlanFormatter buildPlanFormatter;

    enum Format {
        TEXT,
        JSON
    }

    @Option(names = "--target", description = "Plan target: build, test, package, native, or ci.")
    private PlanTarget target = PlanTarget.PACKAGE;

    @Option(names = "--reports-dir", description = "Include a project-relative test report output in test/ci plans.")
    private Path reportsDir;

    @Option(names = "--native-image", description = "Path to the native-image executable for native plans.")
    private Path nativeImageExecutable;

    @Option(names = "--format", description = "Output format: text or json.")
    private Format format = Format.TEXT;

    @Mixin
    private CommandProjectDirectory projectDirectory = new CommandProjectDirectory();

    @Spec
    private CommandSpec spec;

    public PlanCommand() {
        this(new ZoltTomlParser(), new BuildPlanService(), new BuildPlanFormatter());
    }

    PlanCommand(
            ZoltTomlParser tomlParser,
            BuildPlanService buildPlanService,
            BuildPlanFormatter buildPlanFormatter) {
        this.tomlParser = tomlParser;
        this.buildPlanService = buildPlanService;
        this.buildPlanFormatter = buildPlanFormatter;
    }

    @Override
    public Integer call() {
        try {
            Path projectRoot = projectDirectory.path();
            ProjectConfig config = tomlParser.parse(projectRoot.resolve("zolt.toml"));
            TestReportSettings reportSettings = TestReportSettings.reportsDirectory(reportsDir);
            BuildPlan plan = buildPlanService.plan(
                    projectRoot,
                    config,
                    target,
                    reportSettings.projectRelativeReportsDirectory(projectRoot),
                    java.util.Optional.ofNullable(nativeImageExecutable));
            if (format == Format.JSON) {
                CommandOutput.printAndFlush(spec, buildPlanFormatter.json(plan));
            } else {
                CommandOutput.printAndFlush(spec, buildPlanFormatter.text(plan));
            }
            return plan.blocked() ? 1 : 0;
        } catch (TestRunException | ZoltConfigException exception) {
            throw CommandFailures.user(spec, exception);
        }
    }
}
