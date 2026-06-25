package com.zolt.cli.command;

import com.zolt.build.TestReportSettings;
import com.zolt.build.TestRunException;
import com.zolt.cli.CommandHumanOutput;
import com.zolt.project.ProjectConfig;
import com.zolt.test.TestPlanException;
import com.zolt.test.TestPlanJsonFormatter;
import com.zolt.test.TestSelection;
import com.zolt.test.TestSelectionException;
import com.zolt.test.TestShardException;
import com.zolt.test.TestShardPlan;
import com.zolt.test.TestShardSpec;
import com.zolt.test.TestSuitePlan;
import com.zolt.test.TestSuitePlanner;
import com.zolt.toml.ZoltConfigException;
import com.zolt.toml.ZoltTomlParser;
import com.zolt.workspace.Workspace;
import com.zolt.workspace.WorkspaceConfigException;
import com.zolt.workspace.WorkspaceDiscoveryService;
import com.zolt.workspace.WorkspaceMember;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

@Command(name = "plan", description = "Show the selected test suite plan without executing tests.")
public final class TestPlanCommand implements Runnable {
    private final ZoltTomlParser tomlParser;
    private final TestSuitePlanner planner;
    private final TestPlanJsonFormatter jsonFormatter;
    private final WorkspaceDiscoveryService workspaceDiscoveryService;

    enum Format {
        TEXT,
        JSON
    }

    @Option(names = "--suite", description = "Plan one configured test suite. Defaults to all.")
    private String suiteName = "all";

    @Option(names = "--shard-count", description = "Plan deterministic suite shards without executing tests.")
    private String shardCountValue;

    @Option(names = "--test", description = "Select one test class or method. May be repeated.")
    private List<String> testSelectors = List.of();

    @Option(names = "--tests", description = "Select test classes by glob-style class-name pattern. May be repeated.")
    private List<String> testPatterns = List.of();

    @Option(names = "--include-tag", description = "Include tests with a JUnit Platform tag. May be repeated.")
    private List<String> includedTags = List.of();

    @Option(names = "--exclude-tag", description = "Exclude tests with a JUnit Platform tag. May be repeated.")
    private List<String> excludedTags = List.of();

    @Option(names = "--reports-dir", description = "Include a project-relative report directory in JSON shard commands.")
    private Path reportsDir;

    @Option(names = "--format", description = "Output format: text or json.")
    private Format format = Format.TEXT;

    @Mixin
    private CommandProjectDirectory projectDirectory = new CommandProjectDirectory();

    @Spec
    private CommandSpec spec;

    public TestPlanCommand() {
        this(
                new ZoltTomlParser(),
                new TestSuitePlanner(),
                new TestPlanJsonFormatter(),
                new WorkspaceDiscoveryService());
    }

    TestPlanCommand(
            ZoltTomlParser tomlParser,
            TestSuitePlanner planner,
            TestPlanJsonFormatter jsonFormatter,
            WorkspaceDiscoveryService workspaceDiscoveryService) {
        this.tomlParser = tomlParser;
        this.planner = planner;
        this.jsonFormatter = jsonFormatter;
        this.workspaceDiscoveryService = workspaceDiscoveryService;
    }

    @Override
    public void run() {
        Path projectRoot = projectDirectory.path().toAbsolutePath().normalize();
        try {
            TestSelection selection = TestSelection.fromCli(
                    testSelectors,
                    testPatterns,
                    includedTags,
                    excludedTags);
            ProjectConfig config = tomlParser.parse(projectRoot.resolve("zolt.toml"));
            TestSuitePlan plan = planner.plan(projectRoot, config, suiteName, selection);
            int shardCount = TestShardSpec.parseShardCount(shardCountValue);
            List<TestShardPlan> shards = shardCount == 0
                    ? List.of()
                    : planner.shardPlans(projectRoot, config, suiteName, selection, shardCount);
            Optional<Path> projectRelativeReportsDir = TestReportSettings.reportsDirectory(reportsDir)
                    .projectRelativeReportsDirectory(projectRoot);
            if (format == Format.JSON) {
                CommandOutput.printAndFlush(spec, jsonFormatter.json(
                        config,
                        projectRoot,
                        workspaceMember(projectRoot),
                        selection,
                        plan,
                        shards,
                        projectRelativeReportsDir));
            } else {
                printPlan(config, plan, shards, projectRoot);
            }
        } catch (TestPlanException
                | TestSelectionException
                | TestShardException
                | TestRunException
                | ZoltConfigException exception) {
            throw CommandFailures.user(spec, exception);
        }
    }

    private Optional<String> workspaceMember(Path projectRoot) {
        try {
            Optional<Workspace> workspace = workspaceDiscoveryService.discover(projectRoot);
            if (workspace.isEmpty()) {
                return Optional.empty();
            }
            Path normalizedRoot = projectRoot.toAbsolutePath().normalize();
            return workspace.orElseThrow().members().stream()
                    .filter(member -> member.directory().equals(normalizedRoot))
                    .map(WorkspaceMember::path)
                    .findFirst();
        } catch (WorkspaceConfigException exception) {
            return Optional.empty();
        }
    }

    private void printPlan(ProjectConfig config, TestSuitePlan plan, List<TestShardPlan> shards, Path projectRoot) {
        CommandHumanOutput output = CommandHumanOutput.of(spec);
        output.line("Test plan for " + config.project().name());
        output.line("suite: " + plan.suiteName());
        output.line("configured suite: " + (plan.configuredSuite() ? "yes" : "no"));
        output.line("test output: " + plan.outputDirectory());
        output.line("matched entries: " + plan.entries().size());
        output.line("empty: " + (plan.empty() ? "yes" : "no"));
        printFilters(output, "class filters", plan.includeClassname(), plan.excludeClassname());
        printFilters(output, "tag filters", plan.includeTag(), plan.excludeTag());
        if (!plan.selectionClassname().isEmpty()
                || !plan.selectionIncludeTag().isEmpty()
                || !plan.selectionExcludeTag().isEmpty()) {
            printFilters(output, "selection class filters", plan.selectionClassname(), List.of());
            printFilters(output, "selection tag filters", plan.selectionIncludeTag(), plan.selectionExcludeTag());
        }
        printList(output, "entries", plan.entries().stream()
                .map(entry -> entry.className())
                .toList());
        printList(output, "missing explicit selectors", plan.missingExplicitClassSelectors());
        printOverlaps(output, plan.overlappingEntries());
        printList(output, "unassigned entries", plan.unassignedEntries());
        printShards(output, shards, projectRoot);
    }

    private static void printFilters(
            CommandHumanOutput output,
            String label,
            List<String> includes,
            List<String> excludes) {
        String include = includes.isEmpty() ? "<none>" : String.join(", ", includes);
        String exclude = excludes.isEmpty() ? "<none>" : String.join(", ", excludes);
        output.line(label + ": include " + include + "; exclude " + exclude);
    }

    private static void printList(CommandHumanOutput output, String label, List<String> values) {
        output.line(label + ": " + values.size());
        for (String value : values) {
            output.line("- " + value);
        }
    }

    private static void printOverlaps(CommandHumanOutput output, Map<String, List<String>> overlaps) {
        output.line("overlapping entries: " + overlaps.size());
        for (Map.Entry<String, List<String>> entry : overlaps.entrySet()) {
            output.line("- " + entry.getKey() + " also matches " + String.join(", ", entry.getValue()));
        }
    }

    private static void printShards(CommandHumanOutput output, List<TestShardPlan> shards, Path projectRoot) {
        if (shards.isEmpty()) {
            return;
        }
        output.line("shards: " + shards.size());
        for (TestShardPlan shard : shards) {
            output.line("- shard "
                    + shard.shard().label()
                    + ": "
                    + shard.entries().size()
                    + " entries, empty: "
                    + (shard.empty() ? "yes" : "no")
                    + ", manifest: "
                    + shard.projectRelativeManifestPath(projectRoot));
        }
    }
}
