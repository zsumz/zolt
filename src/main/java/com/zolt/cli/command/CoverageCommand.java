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
import com.zolt.build.TestReportSettings;
import com.zolt.build.TestRunException;
import com.zolt.build.TestSelection;
import com.zolt.build.TestSelectionException;
import com.zolt.cache.LocalArtifactCache;
import com.zolt.cli.ZoltCli;
import com.zolt.lockfile.LockfileReadException;
import com.zolt.perf.TimingRecorder;
import com.zolt.project.ProjectConfig;
import com.zolt.resolve.ResolveException;
import com.zolt.toml.ZoltConfigException;
import com.zolt.toml.ZoltTomlParser;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import picocli.CommandLine;
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
    private Path execFile = Path.of("target/coverage/jacoco.exec");

    @Option(names = "--xml-report", description = "Project-relative Jacoco XML report path.")
    private Path xmlReport = Path.of("target/coverage/jacoco.xml");

    @Option(names = "--html-dir", description = "Project-relative Jacoco HTML report directory.")
    private Path htmlDirectory = Path.of("target/coverage/html");

    @Option(names = "--reports-dir", description = "Write JUnit XML reports to a project-relative directory.")
    private Path reportsDir = Path.of("target/coverage/test-reports");

    @Option(names = "--cwd", hidden = true)
    private Path workingDirectory = Path.of(".");

    @Option(names = "--cache-root", hidden = true)
    private Path cacheRoot = LocalArtifactCache.defaultRoot();

    @Mixin
    private ZoltCli.TimingOptions timingOptions = new ZoltCli.TimingOptions();

    @Spec
    private CommandSpec spec;

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
            CoverageReportSettings reportSettings = new CoverageReportSettings(
                    !noXml,
                    !noHtml,
                    execFile,
                    xmlReport,
                    htmlDirectory,
                    TestReportSettings.reportsDirectory(reportsDir));
            ProjectConfig config = timings.measure(
                    "config read",
                    () -> new ZoltTomlParser().parse(workingDirectory.resolve("zolt.toml")));
            CoverageResult result = timings.measure(
                    "run coverage",
                    () -> new CoverageService().runCoverage(
                            workingDirectory,
                            config,
                            cacheRoot,
                            testSelection,
                            reportSettings,
                            requestedTestEvents),
                    coverageResult -> Map.of(
                            TimingAttributeKeys.EXEC_FILE, coverageResult.execFile().toString(),
                            TimingAttributeKeys.XML_REPORT, coverageResult.xmlReport().map(Path::toString).orElse("disabled"),
                            TimingAttributeKeys.HTML_DIRECTORY, coverageResult.htmlDirectory().map(Path::toString).orElse("disabled")));
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
                | ZoltConfigException exception) {
            spec.commandLine().getErr().println("error: " + exception.getMessage());
            throw new CommandLine.ExecutionException(spec.commandLine(), exception.getMessage(), exception);
        } finally {
            CommandTimings.print(spec, "coverage", workingDirectory, timingOptions, timings);
        }
    }
}
