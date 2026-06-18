package com.zolt.cli.command;

import com.zolt.build.BuildException;
import com.zolt.build.CoverageException;
import com.zolt.build.CoverageReportSettings;
import com.zolt.build.CoverageResult;
import com.zolt.build.CoverageService;
import com.zolt.build.GroovyCompileException;
import com.zolt.build.JavaRunException;
import com.zolt.build.JavacException;
import com.zolt.build.ResourceCopyException;
import com.zolt.build.SourceDiscoveryException;
import com.zolt.build.TestRunException;
import com.zolt.cache.LocalArtifactCache;
import com.zolt.cli.ZoltCli;
import com.zolt.lockfile.LockfileReadException;
import com.zolt.perf.TimingRecorder;
import com.zolt.project.ProjectConfig;
import com.zolt.resolve.ResolveException;
import com.zolt.test.TestSelection;
import com.zolt.test.TestSelectionException;
import com.zolt.toml.ZoltConfigException;
import com.zolt.toml.ZoltTomlParser;
import com.zolt.workspace.WorkspaceConfigException;
import com.zolt.workspace.WorkspaceCoverageResult;
import com.zolt.workspace.WorkspaceCoverageService;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

@Command(
        name = "coverage",
        mixinStandardHelpOptions = true,
        description = "Run tests with Jacoco instrumentation and write coverage reports.")
public final class CoverageCommand implements Runnable {
    private final ZoltTomlParser tomlParser;
    private final CoverageService coverageService;
    private final WorkspaceCoverageService workspaceCoverageService;

    @Option(names = "--workspace", description = "Run coverage for workspace members and write aggregate reports.")
    private boolean workspace;

    @Option(names = "--all", description = "Select every workspace member.")
    private boolean all;

    @Option(names = "--member", description = "Select a workspace member by declared path. May be repeated.")
    private List<String> members = List.of();

    @Option(names = "--members", split = ",", description = "Select comma-separated workspace members by declared path.")
    private List<String> memberGroups = List.of();

    @Option(names = "--test", description = "Select one test class or method. May be repeated.")
    private List<String> testSelectors = List.of();

    @Option(names = "--tests", description = "Select test classes by glob-style class-name pattern. May be repeated.")
    private List<String> testPatterns = List.of();

    @Option(names = "--include-tag", description = "Include tests with a JUnit Platform tag. May be repeated.")
    private List<String> includedTags = List.of();

    @Option(names = "--exclude-tag", description = "Exclude tests with a JUnit Platform tag. May be repeated.")
    private List<String> excludedTags = List.of();

    @Option(names = "--test-event", description = "Show JUnit test events: passed, skipped, or failed. May be repeated.")
    private List<String> testEvents = List.of();

    @Option(names = "--no-xml", description = "Disable the Jacoco XML report.")
    private boolean noXml;

    @Option(names = "--no-html", description = "Disable the Jacoco HTML report.")
    private boolean noHtml;

    @Option(names = "--exec-file", description = "Project-relative Jacoco execution data path.")
    private Path execFile;

    @Option(names = "--xml-report", description = "Project-relative Jacoco XML report path.")
    private Path xmlReport;

    @Option(names = "--html-dir", description = "Project-relative Jacoco HTML report directory.")
    private Path htmlDirectory;

    @Option(names = "--reports-dir", description = "Write JUnit XML reports to a project-relative directory.")
    private Path reportsDir;

    @Option(names = "--cwd", hidden = true)
    private Path workingDirectory = Path.of(".");

    @Option(names = "--cache-root", hidden = true)
    private Path cacheRoot = LocalArtifactCache.defaultRoot();

    @Mixin
    private ZoltCli.TimingOptions timingOptions = new ZoltCli.TimingOptions();

    @Spec
    private CommandSpec spec;

    public CoverageCommand() {
        this(new ZoltTomlParser(), new CoverageService(), new WorkspaceCoverageService());
    }

    CoverageCommand(
            ZoltTomlParser tomlParser,
            CoverageService coverageService,
            WorkspaceCoverageService workspaceCoverageService) {
        this.tomlParser = tomlParser;
        this.coverageService = coverageService;
        this.workspaceCoverageService = workspaceCoverageService;
    }

    @Override
    public void run() {
        TimingRecorder timings = CommandTimings.recorder(timingOptions);
        try {
            TestSelection testSelection = TestSelection.fromCli(
                    testSelectors,
                    testPatterns,
                    includedTags,
                    excludedTags);
            List<String> requestedTestEvents = CommandTestEvents.validated(testEvents);
            if (workspace) {
                runWorkspaceCoverage(timings, testSelection, coverageReportSettings(Path.of("target")), requestedTestEvents);
                return;
            }
            ProjectConfig config = timings.measure(
                    "config read",
                    () -> tomlParser.parse(workingDirectory.resolve("zolt.toml")));
            CoverageReportSettings reportSettings = coverageReportSettings(Path.of(config.build().outputRoot()));
            CoverageResult result = timings.measure(
                    "run coverage",
                    () -> coverageService.runCoverage(
                            workingDirectory,
                            config,
                            cacheRoot,
                            testSelection,
                            reportSettings,
                            requestedTestEvents),
                    coverageResult -> Map.of(
                            CommandAttributeKeys.EXEC_FILE, coverageResult.execFile().toString(),
                            CommandAttributeKeys.XML_REPORT, coverageResult.xmlReport().map(Path::toString).orElse("disabled"),
                            CommandAttributeKeys.HTML_DIRECTORY, coverageResult.htmlDirectory().map(Path::toString).orElse("disabled")));
            CommandOutput.printAndFlush(spec, result.testRunResult().output());
            if (!result.testRunResult().output().isEmpty() && !result.testRunResult().output().endsWith("\n")) {
                spec.commandLine().getOut().println();
            }
            if (!result.reportOutput().isBlank()) {
                CommandOutput.printAndFlush(spec, result.reportOutput());
                if (!result.reportOutput().endsWith("\n")) {
                    spec.commandLine().getOut().println();
                }
            }
            spec.commandLine().getOut().println("Coverage reports written");
            spec.commandLine().getOut().println("Execution data: " + result.execFile());
            result.xmlReport().ifPresent(path -> spec.commandLine().getOut().println("XML report: " + path));
            result.htmlDirectory().ifPresent(path -> spec.commandLine().getOut().println("HTML report: " + path));
            result.testRunResult().reportsDirectory()
                    .ifPresent(path -> spec.commandLine().getOut().println("Test reports: " + path));
        } catch (BuildException
                | CoverageException
                | JavacException
                | GroovyCompileException
                | JavaRunException
                | ResourceCopyException
                | TestRunException
                | TestSelectionException
                | SourceDiscoveryException
                | LockfileReadException
                | ResolveException
                | WorkspaceConfigException
                | ZoltConfigException exception) {
            throw CommandFailures.user(spec, exception);
        } finally {
            CommandTimings.print(spec, "coverage", workingDirectory, timingOptions, timings);
        }
    }

    private CoverageReportSettings coverageReportSettings(Path outputRoot) {
        return CoverageReportSettings.forOutputRoot(
                !noXml,
                !noHtml,
                outputRoot,
                execFile,
                xmlReport,
                htmlDirectory,
                reportsDir);
    }

    private void runWorkspaceCoverage(
            TimingRecorder timings,
            TestSelection testSelection,
            CoverageReportSettings reportSettings,
            List<String> requestedTestEvents) {
        WorkspaceCoverageResult result = timings.measure(
                "workspace coverage",
                () -> workspaceCoverageService.runCoverage(
                        workingDirectory,
                        cacheRoot,
                        CommandWorkspaceSelections.from(all, members, memberGroups),
                        testSelection,
                        reportSettings,
                        requestedTestEvents),
                coverageResult -> {
                    Map<String, String> attributes = new java.util.LinkedHashMap<>(
                            CommandTestAttributes.workspaceTest(coverageResult.testResult()));
                    attributes.put(CommandAttributeKeys.EXEC_FILE, coverageResult.execFile().toString());
                    attributes.put(CommandAttributeKeys.XML_REPORT, coverageResult.xmlReport().map(Path::toString).orElse("disabled"));
                    attributes.put(CommandAttributeKeys.HTML_DIRECTORY, coverageResult.htmlDirectory().map(Path::toString).orElse("disabled"));
                    return attributes;
                });
        for (WorkspaceCoverageResult.MemberCoverageRunResult member : result.members()) {
            CommandOutput.printAndFlush(spec, member.result().output());
            if (!member.result().output().isEmpty() && !member.result().output().endsWith("\n")) {
                spec.commandLine().getOut().println();
            }
            spec.commandLine().getOut().println("Coverage tests passed in " + member.member());
            member.result().reportsDirectory().ifPresent(directory ->
                    spec.commandLine().getOut().println("Wrote coverage test reports for "
                            + member.member()
                            + " to "
                            + directory));
        }
        if (!result.reportOutput().isBlank()) {
            CommandOutput.printAndFlush(spec, result.reportOutput());
            if (!result.reportOutput().endsWith("\n")) {
                spec.commandLine().getOut().println();
            }
        }
        spec.commandLine().getOut().println("Workspace coverage reports written");
        spec.commandLine().getOut().println("Execution data: " + result.execFile());
        result.xmlReport().ifPresent(path -> spec.commandLine().getOut().println("XML report: " + path));
        result.htmlDirectory().ifPresent(path -> spec.commandLine().getOut().println("HTML report: " + path));
        spec.commandLine().getOut().println(
                "Coverage passed for "
                        + result.members().size()
                        + " workspace members");
    }
}
