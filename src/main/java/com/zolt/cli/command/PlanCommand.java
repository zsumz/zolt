package com.zolt.cli.command;

import com.zolt.build.TestReportSettings;
import com.zolt.build.TestRunException;
import com.zolt.plan.BuildPlan;
import com.zolt.plan.BuildPlanFormatter;
import com.zolt.plan.BuildPlanService;
import com.zolt.plan.PlanTarget;
import com.zolt.project.ProjectConfig;
import com.zolt.toml.ZoltConfigException;
import com.zolt.toml.ZoltTomlParser;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
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

    @Option(names = "--target", description = "Plan target: build, test, package, or ci.")
    private PlanTarget target = PlanTarget.PACKAGE;

    @Option(names = "--reports-dir", description = "Include a project-relative test report output in test/ci plans.")
    private Path reportsDir;

    @Option(names = "--format", description = "Output format: text or json.")
    private Format format = Format.TEXT;

    @Option(names = "--cwd", hidden = true)
    private Path workingDirectory = Path.of(".");

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
            ProjectConfig config = tomlParser.parse(workingDirectory.resolve("zolt.toml"));
            TestReportSettings reportSettings = TestReportSettings.reportsDirectory(reportsDir);
            BuildPlan plan = buildPlanService.plan(
                    workingDirectory,
                    config,
                    target,
                    reportSettings.projectRelativeReportsDirectory(workingDirectory));
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
