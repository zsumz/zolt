package com.zolt.build;

import com.zolt.classpath.ClasspathBuilder;
import com.zolt.classpath.ClasspathSet;
import com.zolt.doctor.JdkDetector;
import com.zolt.doctor.JdkStatus;
import com.zolt.lockfile.ZoltLockfile;
import com.zolt.lockfile.ZoltLockfileReader;
import com.zolt.project.ProjectConfig;
import com.zolt.quarkus.QuarkusAugmentationException;
import com.zolt.quarkus.QuarkusTestApplicationModelService;
import com.zolt.resolve.Classpath;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.StringJoiner;

public final class TestRunService {
    private static final String CONSOLE_MAIN_CLASS = "org.junit.platform.console.ConsoleLauncher";
    private static final String JBOSS_LOG_MANAGER_PROPERTY =
            "-Djava.util.logging.manager=org.jboss.logmanager.LogManager";

    private final TestCompileService testCompileService;
    private final ZoltLockfileReader lockfileReader;
    private final ClasspathBuilder classpathBuilder;
    private final JdkDetector jdkDetector;
    private final JavaRunner javaRunner;
    private final QuarkusTestApplicationModelService quarkusTestApplicationModelService;
    private final String pathSeparator;

    public TestRunService() {
        this(
                new TestCompileService(),
                new ZoltLockfileReader(),
                new ClasspathBuilder(),
                new JdkDetector(),
                new JavaRunner(),
                new QuarkusTestApplicationModelService(),
                java.io.File.pathSeparator);
    }

    TestRunService(
            TestCompileService testCompileService,
            ZoltLockfileReader lockfileReader,
            ClasspathBuilder classpathBuilder,
            JdkDetector jdkDetector,
            JavaRunner javaRunner,
            QuarkusTestApplicationModelService quarkusTestApplicationModelService,
            String pathSeparator) {
        this.testCompileService = testCompileService;
        this.lockfileReader = lockfileReader;
        this.classpathBuilder = classpathBuilder;
        this.jdkDetector = jdkDetector;
        this.javaRunner = javaRunner;
        this.quarkusTestApplicationModelService = quarkusTestApplicationModelService;
        this.pathSeparator = pathSeparator;
    }

    public TestRunResult runTests(Path projectDirectory, ProjectConfig config, Path cacheRoot) {
        TestCompileResult compileResult = testCompileService.compileTests(projectDirectory, config, cacheRoot);
        ZoltLockfile lockfile = lockfileReader.read(projectDirectory.resolve("zolt.lock"));
        ClasspathSet classpaths = classpathBuilder.build(lockfileReader.classpathPackages(lockfile, cacheRoot));
        return runTests(projectDirectory, config, classpaths, compileResult);
    }

    public TestRunResult runTests(
            Path projectDirectory,
            ProjectConfig config,
            ClasspathSet classpaths,
            BuildResult buildResult) {
        TestCompileResult compileResult = testCompileService.compileTests(
                projectDirectory,
                config,
                classpaths,
                buildResult);
        return runTests(projectDirectory, config, classpaths, compileResult);
    }

    private TestRunResult runTests(
            Path projectDirectory,
            ProjectConfig config,
            ClasspathSet classpaths,
            TestCompileResult compileResult) {
        List<Path> runnerClasspath = new ArrayList<>();
        runnerClasspath.add(compileResult.outputDirectory());
        runnerClasspath.add(compileResult.buildResult().outputDirectory());
        runnerClasspath.addAll(classpaths.test().entries());
        runnerClasspath = absolutePaths(runnerClasspath);
        List<Path> launcherClasspath = testRunnerClasspath(config, runnerClasspath);
        if (launcherClasspath.stream().noneMatch(TestRunService::isConsoleJar)) {
            throw new TestRunException(
                    "JUnit Platform Console is not present on the test classpath. "
                            + "Run `zolt resolve` to refresh Zolt's test runner tooling. "
                            + "Keep JUnit, Spock, and other test engines declared in [test.dependencies].");
        }

        JdkStatus jdkStatus = jdkDetector.detect(config.project().java());
        if (!jdkStatus.ok()) {
            throw new BuildException("JDK check failed. " + String.join(" ", jdkStatus.problems()));
        }
        JavaRunResult result = javaRunner.run(
                jdkStatus.java().orElseThrow(),
                new Classpath(launcherClasspath),
                CONSOLE_MAIN_CLASS,
                jvmArguments(projectDirectory, config, launcherClasspath),
                List.of(
                        "execute",
                        "--disable-banner",
                        "--class-path", joined(runnerClasspath),
                        "--scan-class-path",
                        "--details", "summary"));
        failOnHiddenQuarkusBootstrapFailure(config, result.output());
        return new TestRunResult(compileResult, result.output());
    }

    private String joined(List<Path> classpath) {
        StringJoiner joiner = new StringJoiner(pathSeparator);
        for (Path entry : classpath) {
            joiner.add(entry.normalize().toString());
        }
        return joiner.toString();
    }

    private static List<Path> absolutePaths(List<Path> classpath) {
        return classpath.stream()
                .map(path -> path.toAbsolutePath().normalize())
                .toList();
    }

    static List<Path> testRunnerClasspath(ProjectConfig config, List<Path> classpath) {
        if (config == null || !config.frameworkSettings().quarkus().enabled()) {
            return List.copyOf(classpath);
        }
        return classpath.stream()
                .filter(path -> !isQuarkusBuilderJar(path))
                .toList();
    }

    private static boolean isQuarkusBuilderJar(Path path) {
        String normalized = path.normalize().toString().replace('\\', '/');
        String name = path.getFileName() == null ? "" : path.getFileName().toString();
        return normalized.contains("/io/quarkus/quarkus-builder/")
                && name.startsWith("quarkus-builder-")
                && name.endsWith(".jar");
    }

    private static boolean isConsoleJar(Path path) {
        String name = path.getFileName() == null ? "" : path.getFileName().toString();
        return name.startsWith("junit-platform-console") && name.endsWith(".jar");
    }

    private List<String> jvmArguments(
            Path projectDirectory,
            ProjectConfig config,
            List<Path> runnerClasspath) {
        List<String> arguments = new ArrayList<>();
        arguments.add("-Duser.dir=" + projectDirectory.toAbsolutePath().normalize());
        writeQuarkusTestApplicationModel(projectDirectory, config)
                .ifPresent(path -> arguments.add("-D"
                        + QuarkusTestApplicationModelService.SERIALIZED_TEST_MODEL_PROPERTY
                        + "="
                        + path));
        if (runnerClasspath.stream().anyMatch(TestRunService::isJbossLogManagerJar)) {
            arguments.add(JBOSS_LOG_MANAGER_PROPERTY);
        }
        return List.copyOf(arguments);
    }

    private Optional<Path> writeQuarkusTestApplicationModel(Path projectDirectory, ProjectConfig config) {
        try {
            return quarkusTestApplicationModelService.writeIfEnabled(projectDirectory, config);
        } catch (QuarkusAugmentationException exception) {
            throw new TestRunException(
                    "Could not prepare Quarkus test application model. "
                            + "Run `zolt resolve`, then run `zolt test` again. "
                            + exception.getMessage(),
                    exception);
        }
    }

    static void failOnHiddenQuarkusBootstrapFailure(ProjectConfig config, String output) {
        if (!config.frameworkSettings().quarkus().enabled()) {
            return;
        }
        if (output == null || output.isBlank()) {
            return;
        }
        if (!hiddenQuarkusBootstrapFailure(output)) {
            return;
        }
        throw new TestRunException(
                "Quarkus test bootstrap failed while JUnit Platform reported success. "
                        + "Zolt supports an early Quarkus test runner path, but this project hit an unsupported "
                        + "Quarkus test bootstrap shape. Use plain JUnit tests for now, or simplify `@QuarkusTest` "
                        + "usage until Zolt's dedicated Quarkus test runner is expanded.\n"
                        + output.stripTrailing());
    }

    private static boolean hiddenQuarkusBootstrapFailure(String output) {
        return output.contains("io.quarkus.test.junit")
                && (output.contains("io.quarkus.bootstrap.BootstrapException")
                        || output.contains("ClassCastException: class io.quarkus.builder.BuildChainBuilder")
                        || output.contains("NullPointerException"));
    }

    private static boolean isJbossLogManagerJar(Path path) {
        String name = path.getFileName() == null ? "" : path.getFileName().toString();
        return name.startsWith("jboss-logmanager-") && name.endsWith(".jar");
    }
}
