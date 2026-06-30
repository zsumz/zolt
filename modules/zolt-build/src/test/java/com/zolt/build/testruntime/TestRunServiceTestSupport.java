package com.zolt.build.testruntime;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.build.run.JavaRunner;
import com.zolt.build.testruntime.compile.TestCompileService;
import com.zolt.doctor.JdkChecker;
import com.zolt.doctor.JdkDetector;
import com.zolt.doctor.JdkStatus;
import com.zolt.framework.FrameworkTestRunRequest;
import com.zolt.framework.FrameworkTestRunResult;
import com.zolt.framework.FrameworkTestRunner;
import com.zolt.build.junit.PlainJunitWorkerRunner;
import com.zolt.project.BuildSettings;
import com.zolt.project.FrameworkSettings;
import com.zolt.project.ProjectConfig;
import com.zolt.project.ProjectConfigs;
import com.zolt.project.ProjectMetadata;
import com.zolt.project.QuarkusPackageMode;
import com.zolt.project.QuarkusSettings;
import com.zolt.project.TestRuntimeSettings;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

public final class TestRunServiceTestSupport {
    private TestRunServiceTestSupport() {
    }

    static TestRunService service(JavaRunner.ProcessRunner processRunner) {
        return service(processRunner, new JdkDetector());
    }

    static TestRunService service(JavaRunner.ProcessRunner processRunner, JdkChecker jdkChecker) {
        return service(
                processRunner,
                jdkChecker,
                FrameworkTestRunner.none());
    }

    static TestRunService service(
            JavaRunner.ProcessRunner processRunner,
            FrameworkTestRunner frameworkTestRunner) {
        return service(
                processRunner,
                new JdkDetector(),
                frameworkTestRunner);
    }

    static TestRunService service(
            JavaRunner.ProcessRunner processRunner,
            JdkChecker jdkChecker,
            FrameworkTestRunner frameworkTestRunner) {
        return new TestRunService(
                new TestCompileService(jdkChecker),
                jdkChecker,
                new JavaRunner(":", processRunner),
                frameworkTestRunner,
                () -> List.of(Path.of("/zolt/zolt.jar")),
                (javaExecutable, workerClasspath, projectDirectory, testRuntimeClasspath, testOutputDirectory, testSelection, jvmArguments, environment, reportsDirectory, testEvents, profileDirectory) -> {
                    throw new AssertionError("Plain JUnit worker should not run for this test.");
                },
                false,
                ":");
    }

    static TestRunService service(
            JavaRunner.ProcessRunner processRunner,
            JdkChecker jdkChecker,
            FrameworkTestRunner frameworkTestRunner,
            Supplier<List<Path>> plainJunitWorkerClasspath,
            PlainJunitWorkerRunner plainJunitWorkerRunner,
            boolean plainJunitWorkerEnabled) {
        return new TestRunService(
                new TestCompileService(jdkChecker),
                jdkChecker,
                new JavaRunner(":", processRunner),
                frameworkTestRunner,
                plainJunitWorkerClasspath,
                plainJunitWorkerRunner,
                plainJunitWorkerEnabled,
                ":");
    }

    static FrameworkTestRunner enabledFrameworkTestRunner(
            Function<FrameworkTestRunRequest, Optional<FrameworkTestRunResult>> runner) {
        return enabledFrameworkTestRunner(runner, null);
    }

    static FrameworkTestRunner enabledFrameworkTestRunner(
            Function<FrameworkTestRunRequest, Optional<FrameworkTestRunResult>> runner,
            String unsupportedReportsMessage) {
        return new FrameworkTestRunner() {
            @Override
            public Optional<FrameworkTestRunResult> runIfEnabled(FrameworkTestRunRequest request) {
                return runner.apply(request);
            }

            @Override
            public boolean isEnabled(ProjectConfig config) {
                return true;
            }

            @Override
            public String testRunnerName() {
                return "quarkus-test-worker";
            }

            @Override
            public Optional<String> unsupportedReportsMessage() {
                return Optional.ofNullable(unsupportedReportsMessage);
            }
        };
    }

    static void source(Path projectDir, String path, String content) throws IOException {
        Path source = projectDir.resolve(path);
        Files.createDirectories(source.getParent());
        Files.writeString(source, content);
    }

    static ProjectConfig config() {
        return ProjectConfigs.withDirectDependencies(
                new ProjectMetadata("demo", "0.1.0", "com.example", currentJavaMajorVersion(), Optional.of("com.example.Main")),
                Map.of("central", "https://repo.maven.apache.org/maven2"),
                Map.of(),
                Map.of("org.junit.platform:junit-platform-console-standalone", "1.11.4"),
                BuildSettings.defaults());
    }

    static ProjectConfig configWithTestRuntime() {
        return config().withBuildSettings(BuildSettings.defaults().withTestRuntime(new TestRuntimeSettings(
                List.of("-Dconfigured=true"),
                Map.of("logs.dir", "${project.root}/test-logs"),
                Map.of("TZ", "America/Chicago", "APP_HOME", "${project.root}"),
                List.of("failed"))));
    }

    static ProjectConfig multiRootConfig() {
        return ProjectConfigs.withDirectDependencies(
                new ProjectMetadata("demo", "0.1.0", "com.example", currentJavaMajorVersion(), Optional.of("com.example.Main")),
                Map.of("central", "https://repo.maven.apache.org/maven2"),
                Map.of(),
                Map.of("org.junit.platform:junit-platform-console-standalone", "1.11.4"),
                new BuildSettings(
                        "src/main/java",
                        "src/test/java",
                        "target/classes",
                        "target/test-classes",
                        List.of("src/test/java", "src/integration-test/java")));
    }

    static ProjectConfig quarkusConfig() {
        return config().withFrameworkSettings(new FrameworkSettings(
                new QuarkusSettings(true, QuarkusPackageMode.FAST_JAR)));
    }

    static String launcherClasspath(List<String> command) {
        return commandArgumentAfter(command, "-classpath");
    }

    static String commandArgumentAfter(List<String> command, String argument) {
        int index = command.indexOf(argument);
        assertTrue(index >= 0, "missing command argument " + argument + " in " + command);
        assertTrue(index + 1 < command.size(), "missing value after command argument " + argument + " in " + command);
        return command.get(index + 1);
    }

    private static String currentJavaMajorVersion() {
        String version = System.getProperty("java.version");
        String[] parts = version.split("[._+-]", -1);
        if (parts.length >= 2 && "1".equals(parts[0])) {
            return parts[1];
        }
        return parts[0];
    }

    private static String executable(String name) {
        return System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT).contains("win")
                ? name + ".exe"
                : name;
    }

    public static final class CachingJdkChecker implements JdkChecker {
        private int detectCalls;
        private int toolchainReads;
        private JdkStatus status;

        @Override
        public JdkStatus detect(String requiredVersion) {
            detectCalls++;
            if (status == null) {
                toolchainReads++;
                Path javaHome = Path.of(System.getProperty("java.home"));
                status = new JdkStatus(
                        Optional.of(javaHome),
                        Optional.of(javaHome.resolve("bin").resolve(executable("java"))),
                        Optional.of(javaHome.resolve("bin").resolve(executable("javac"))),
                        Optional.of(javaHome.resolve("bin").resolve(executable("jar"))),
                        Optional.of(requiredVersion),
                        requiredVersion);
            }
            return status;
        }

        public int detectCalls() {
            return detectCalls;
        }

        public int toolchainReads() {
            return toolchainReads;
        }
    }
}
