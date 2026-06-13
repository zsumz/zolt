package com.zolt.cli.command;

import com.zolt.cache.LocalArtifactCache;
import com.zolt.cli.ZoltCli;
import com.zolt.lockfile.LockfileReadException;
import com.zolt.perf.TimingRecorder;
import com.zolt.project.ProjectConfig;
import com.zolt.quarkus.QuarkusAugmentationRequestFactory;
import com.zolt.quarkus.QuarkusPlan;
import com.zolt.quarkus.QuarkusPlanException;
import com.zolt.quarkus.QuarkusPlanFormatter;
import com.zolt.quarkus.QuarkusPlanService;
import com.zolt.quarkus.QuarkusTestPlan;
import com.zolt.quarkus.QuarkusTestPlanFormatter;
import com.zolt.quarkus.QuarkusTestPlanService;
import com.zolt.toml.ZoltConfigException;
import com.zolt.toml.ZoltTomlParser;
import java.nio.file.Path;
import java.util.Map;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

@Command(
        name = "quarkus",
        mixinStandardHelpOptions = true,
        description = "Inspect Quarkus build-time augmentation inputs.",
        subcommands = {
                QuarkusCommand.PlanCommand.class,
                QuarkusCommand.TestPlanCommand.class
        })
public final class QuarkusCommand implements Runnable {
    @Spec
    private CommandSpec spec;

    @Override
    public void run() {
        spec.commandLine().usage(spec.commandLine().getOut());
    }

    @Command(name = "plan", description = "Print the Quarkus augmentation input plan.")
    public static final class PlanCommand implements Runnable {
        private final ZoltTomlParser tomlParser;
        private final QuarkusPlanService quarkusPlanService;
        private final QuarkusPlanFormatter quarkusPlanFormatter;
        private final QuarkusAugmentationRequestFactory augmentationRequestFactory;

        @Option(names = "--cwd", hidden = true)
        private Path workingDirectory = Path.of(".");

        @Option(names = "--cache-root", hidden = true)
        private Path cacheRoot = LocalArtifactCache.defaultRoot();

        @Mixin
        private ZoltCli.TimingOptions timingOptions = new ZoltCli.TimingOptions();

        @Spec
        private CommandSpec spec;

        public PlanCommand() {
            this(
                    new ZoltTomlParser(),
                    new QuarkusPlanService(),
                    new QuarkusPlanFormatter(),
                    new QuarkusAugmentationRequestFactory());
        }

        PlanCommand(
                ZoltTomlParser tomlParser,
                QuarkusPlanService quarkusPlanService,
                QuarkusPlanFormatter quarkusPlanFormatter,
                QuarkusAugmentationRequestFactory augmentationRequestFactory) {
            this.tomlParser = tomlParser;
            this.quarkusPlanService = quarkusPlanService;
            this.quarkusPlanFormatter = quarkusPlanFormatter;
            this.augmentationRequestFactory = augmentationRequestFactory;
        }

        @Override
        public void run() {
            TimingRecorder timings = CommandTimings.recorder(timingOptions);
            try {
                ProjectConfig config = timings.measure(
                        "config read",
                        () -> tomlParser.parse(workingDirectory.resolve("zolt.toml")));
                QuarkusPlan plan = timings.measure(
                        "quarkus plan",
                        () -> quarkusPlanService.plan(workingDirectory, config, cacheRoot),
                        QuarkusCommand::quarkusPlanAttributes);
                String output = timings.measure(
                        "quarkus plan format",
                        () -> quarkusPlanFormatter.format(plan));
                CommandOutput.printAndFlush(spec, output);
                timings.measure(
                        "quarkus augmentation request",
                        () -> augmentationRequestFactory.create(plan));
            } catch (LockfileReadException | QuarkusPlanException | ZoltConfigException exception) {
                throw CommandFailures.user(spec, exception);
            } finally {
                CommandTimings.print(spec, "quarkus plan", workingDirectory, timingOptions, timings);
            }
        }
    }

    @Command(name = "test-plan", description = "Print the Quarkus test bootstrap plan.")
    public static final class TestPlanCommand implements Runnable {
        private final ZoltTomlParser tomlParser;
        private final QuarkusTestPlanService quarkusTestPlanService;
        private final QuarkusTestPlanFormatter quarkusTestPlanFormatter;

        @Option(names = "--cwd", hidden = true)
        private Path workingDirectory = Path.of(".");

        @Spec
        private CommandSpec spec;

        public TestPlanCommand() {
            this(new ZoltTomlParser(), new QuarkusTestPlanService(), new QuarkusTestPlanFormatter());
        }

        TestPlanCommand(
                ZoltTomlParser tomlParser,
                QuarkusTestPlanService quarkusTestPlanService,
                QuarkusTestPlanFormatter quarkusTestPlanFormatter) {
            this.tomlParser = tomlParser;
            this.quarkusTestPlanService = quarkusTestPlanService;
            this.quarkusTestPlanFormatter = quarkusTestPlanFormatter;
        }

        @Override
        public void run() {
            try {
                ProjectConfig config = tomlParser.parse(workingDirectory.resolve("zolt.toml"));
                QuarkusTestPlan plan = quarkusTestPlanService.plan(workingDirectory, config);
                CommandOutput.printAndFlush(spec, quarkusTestPlanFormatter.format(plan));
            } catch (QuarkusPlanException | ZoltConfigException exception) {
                throw CommandFailures.user(spec, exception);
            }
        }
    }

    private static Map<String, String> quarkusPlanAttributes(QuarkusPlan plan) {
        return Map.of(
                TimingAttributeKeys.RUNTIME_CLASSPATH_ENTRIES, Integer.toString(plan.runtimeClasspath().size()),
                TimingAttributeKeys.DEPLOYMENT_CLASSPATH_ENTRIES, Integer.toString(plan.deploymentClasspath().size()),
                TimingAttributeKeys.EXTENSIONS, Integer.toString(plan.extensions().size()),
                TimingAttributeKeys.PACKAGE_MODE, plan.packageMode().configValue());
    }
}
