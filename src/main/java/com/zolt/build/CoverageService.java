package com.zolt.build;

import com.zolt.doctor.JdkChecker;
import com.zolt.doctor.JdkDetector;
import com.zolt.doctor.JdkStatus;
import com.zolt.lockfile.ZoltLockfile;
import com.zolt.lockfile.ZoltLockfileReader;
import com.zolt.project.ProjectConfig;
import com.zolt.dependency.DependencyScope;
import com.zolt.dependency.PackageId;
import com.zolt.classpath.Classpath;
import com.zolt.classpath.LockfileClasspathPackageConverter;
import com.zolt.classpath.ResolvedClasspathPackage;
import com.zolt.resolve.ResolveService;
import com.zolt.test.TestSelection;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public final class CoverageService {
    private static final String JACOCO_CLI_MAIN_CLASS = "org.jacoco.cli.internal.Main";
    private static final PackageId JACOCO_AGENT_PACKAGE = new PackageId("org.jacoco", "org.jacoco.agent");
    private static final PackageId JACOCO_CLI_PACKAGE = new PackageId("org.jacoco", "org.jacoco.cli");

    private final CoverageTestRunner testRunner;
    private final ZoltLockfileReader lockfileReader;
    private final JdkChecker jdkDetector;
    private final JavaRunner javaRunner;
    private final CoverageToolingResolver toolingResolver;

    public CoverageService() {
        this(
                new TestRunService()::runTests,
                new ZoltLockfileReader(),
                new JdkDetector(),
                new JavaRunner(),
                new ResolveService()::resolveWithCoverageTooling);
    }

    CoverageService(
            CoverageTestRunner testRunner,
            ZoltLockfileReader lockfileReader,
            JdkChecker jdkDetector,
            JavaRunner javaRunner,
            CoverageToolingResolver toolingResolver) {
        this.testRunner = testRunner;
        this.lockfileReader = lockfileReader;
        this.jdkDetector = jdkDetector;
        this.javaRunner = javaRunner;
        this.toolingResolver = toolingResolver;
    }

    public CoverageResult runCoverage(
            Path projectDirectory,
            ProjectConfig config,
            Path cacheRoot,
            TestSelection selection,
            CoverageReportSettings reportSettings,
            List<String> cliEvents) {
        CoverageReportSettings settings = reportSettings == null ? CoverageReportSettings.defaults() : reportSettings;
        Path projectRoot = projectDirectory.toAbsolutePath().normalize();
        toolingResolver.resolve(projectRoot, config, cacheRoot);
        List<ResolvedClasspathPackage> coveragePackages = coveragePackages(projectRoot, cacheRoot);
        Path agentJar = coverageArtifact(coveragePackages, JACOCO_AGENT_PACKAGE)
                .orElseThrow(() -> missingTool("org.jacoco:org.jacoco.agent"));
        if (!agentJar.getFileName().toString().contains("-runtime")) {
            throw new CoverageException(
                    "Coverage requires locked Jacoco runtime agent artifact `org.jacoco:org.jacoco.agent:runtime`. "
                            + "Run `zolt resolve` to refresh coverage tooling.");
        }
        List<Path> cliClasspath = coveragePackages.stream()
                .map(dependency -> dependency.resolvedPackage().jarPath().toAbsolutePath().normalize())
                .sorted(Comparator.comparing(Path::toString))
                .toList();
        if (coverageArtifact(coveragePackages, JACOCO_CLI_PACKAGE).isEmpty()) {
            throw missingTool("org.jacoco:org.jacoco.cli");
        }

        Path execFile = settings.absoluteExecFile(projectRoot);
        createParent(execFile);
        TestJvmArguments coverageJvmArguments = new TestJvmArguments(List.of(
                "-javaagent:" + agentJar.toAbsolutePath().normalize() + "=destfile=" + execFile + ",append=false"));
        TestRunResult testResult = testRunner.runTests(
                projectRoot,
                config,
                cacheRoot,
                selection,
                coverageJvmArguments,
                settings.testReports(),
                cliEvents);
        JavaRunResult reportResult = runReport(projectRoot, config, settings, execFile, cliClasspath);
        return new CoverageResult(
                testResult,
                reportResult.output(),
                execFile,
                settings.absoluteXmlReport(projectRoot),
                settings.absoluteHtmlDirectory(projectRoot));
    }

    private JavaRunResult runReport(
            Path projectRoot,
            ProjectConfig config,
            CoverageReportSettings settings,
            Path execFile,
            List<Path> cliClasspath) {
        JdkStatus jdkStatus = jdkDetector.detect(config.project().java());
        if (!jdkStatus.ok()) {
            throw new CoverageException("JDK check failed. " + String.join(" ", jdkStatus.problems()));
        }
        List<String> arguments = new ArrayList<>();
        arguments.add("report");
        arguments.add(execFile.toString());
        arguments.add("--classfiles");
        arguments.add(projectRoot.resolve(config.build().output()).normalize().toString());
        arguments.add("--sourcefiles");
        arguments.add(projectRoot.resolve(config.build().source()).normalize().toString());
        settings.absoluteXmlReport(projectRoot).ifPresent(path -> {
            createParent(path);
            arguments.add("--xml");
            arguments.add(path.toString());
        });
        settings.absoluteHtmlDirectory(projectRoot).ifPresent(path -> {
            createDirectory(path);
            arguments.add("--html");
            arguments.add(path.toString());
        });
        try {
            return javaRunner.run(
                    jdkStatus.java().orElseThrow(),
                    new Classpath(cliClasspath),
                    JACOCO_CLI_MAIN_CLASS,
                    List.of(),
                    arguments);
        } catch (JavaRunException exception) {
            throw new CoverageException(
                    "Coverage report generation failed. Check Jacoco output, test classes, and source paths, then run `zolt coverage` again.\n"
                            + exception.getMessage(),
                    exception);
        }
    }

    private List<ResolvedClasspathPackage> coveragePackages(Path projectDirectory, Path cacheRoot) {
        ZoltLockfile lockfile = lockfileReader.read(projectDirectory.resolve("zolt.lock"));
        List<ResolvedClasspathPackage> packages = LockfileClasspathPackageConverter.classpathPackages(lockfile, cacheRoot).stream()
                .filter(dependency -> dependency.scope() == DependencyScope.TOOL_COVERAGE)
                .toList();
        if (packages.isEmpty()) {
            throw new CoverageException(
                    "Coverage requires locked tooling in scope `tool-coverage`. Run `zolt resolve` to refresh Jacoco tooling, then run `zolt coverage` again.");
        }
        return packages;
    }

    private static Optional<Path> coverageArtifact(List<ResolvedClasspathPackage> packages, PackageId packageId) {
        return packages.stream()
                .filter(dependency -> dependency.resolvedPackage().packageId().equals(packageId))
                .map(dependency -> dependency.resolvedPackage().jarPath())
                .findFirst();
    }

    private static CoverageException missingTool(String coordinate) {
        return new CoverageException(
                "Coverage requires locked tooling artifact `" + coordinate + "` in scope `tool-coverage`. "
                        + "Run `zolt resolve` to refresh Jacoco tooling, then run `zolt coverage` again.");
    }

    private static void createParent(Path path) {
        Path parent = path.getParent();
        if (parent != null) {
            createDirectory(parent);
        }
    }

    private static void createDirectory(Path path) {
        try {
            Files.createDirectories(path);
        } catch (IOException exception) {
            throw new CoverageException("Could not create coverage output directory " + path + ".", exception);
        }
    }

    @FunctionalInterface
    interface CoverageTestRunner {
        TestRunResult runTests(
                Path projectDirectory,
                ProjectConfig config,
                Path cacheRoot,
                TestSelection selection,
                TestJvmArguments jvmArguments,
                TestReportSettings reportSettings,
                List<String> cliEvents);
    }

    @FunctionalInterface
    interface CoverageToolingResolver {
        void resolve(Path projectDirectory, ProjectConfig config, Path cacheRoot);
    }
}
