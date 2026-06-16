package com.zolt.build;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.zolt.doctor.JdkChecker;
import com.zolt.doctor.JdkDetector;
import com.zolt.doctor.JdkStatus;
import com.zolt.framework.FrameworkTestRunRequest;
import com.zolt.framework.FrameworkTestRunResult;
import com.zolt.framework.FrameworkTestRunner;
import com.zolt.project.BuildSettings;
import com.zolt.project.FrameworkSettings;
import com.zolt.project.ProjectConfig;
import com.zolt.project.ProjectConfigs;
import com.zolt.project.ProjectMetadata;
import com.zolt.project.QuarkusPackageMode;
import com.zolt.project.QuarkusSettings;
import com.zolt.project.TestRuntimeSettings;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

final class TestRunServiceTestSupport {
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
                (javaExecutable, workerClasspath, projectDirectory, testRuntimeClasspath, testOutputDirectory, testSelection, jvmArguments, environment, reportsDirectory, testEvents) -> {
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
            TestRunService.PlainJunitWorkerRunner plainJunitWorkerRunner,
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

    static void writeConsoleLockfile(Path projectDir) throws IOException {
        Files.writeString(projectDir.resolve("zolt.lock"), """
                version = 1

                [[package]]
                id = "org.junit.platform:junit-platform-console-standalone"
                version = "1.11.4"
                source = "maven-central"
                scope = "test"
                direct = true
                jar = "org/junit/platform/junit-platform-console-standalone/1.11.4/junit-platform-console-standalone-1.11.4.jar"
                dependencies = []
                """);
    }

    static void writeConsoleAndJbossLogManagerLockfile(Path projectDir) throws IOException {
        Files.writeString(projectDir.resolve("zolt.lock"), """
                version = 1

                [[package]]
                id = "org.junit.platform:junit-platform-console-standalone"
                version = "1.11.4"
                source = "maven-central"
                scope = "test"
                direct = true
                jar = "org/junit/platform/junit-platform-console-standalone/1.11.4/junit-platform-console-standalone-1.11.4.jar"
                dependencies = []

                [[package]]
                id = "org.jboss.logmanager:jboss-logmanager"
                version = "3.1.2.Final"
                source = "maven-central"
                scope = "test"
                direct = false
                jar = "org/jboss/logmanager/jboss-logmanager/3.1.2.Final/jboss-logmanager-3.1.2.Final.jar"
                dependencies = []
                """);
    }

    static void writeNonStandaloneConsoleLockfile(Path projectDir) throws IOException {
        Files.writeString(projectDir.resolve("zolt.lock"), """
                version = 1

                [[package]]
                id = "org.apiguardian:apiguardian-api"
                version = "1.1.2"
                source = "maven-central"
                scope = "test"
                direct = false
                jar = "org/apiguardian/apiguardian-api/1.1.2/apiguardian-api-1.1.2.jar"
                dependencies = []

                [[package]]
                id = "org.junit.jupiter:junit-jupiter-engine"
                version = "5.11.4"
                source = "maven-central"
                scope = "test"
                direct = true
                jar = "org/junit/jupiter/junit-jupiter-engine/5.11.4/junit-jupiter-engine-5.11.4.jar"
                dependencies = []

                [[package]]
                id = "org.junit.platform:junit-platform-commons"
                version = "1.11.4"
                source = "maven-central"
                scope = "test"
                direct = false
                jar = "org/junit/platform/junit-platform-commons/1.11.4/junit-platform-commons-1.11.4.jar"
                dependencies = []

                [[package]]
                id = "org.junit.platform:junit-platform-console"
                version = "1.11.4"
                source = "maven-central"
                scope = "test"
                direct = false
                jar = "org/junit/platform/junit-platform-console/1.11.4/junit-platform-console-1.11.4.jar"
                dependencies = []

                [[package]]
                id = "org.junit.platform:junit-platform-engine"
                version = "1.11.4"
                source = "maven-central"
                scope = "test"
                direct = false
                jar = "org/junit/platform/junit-platform-engine/1.11.4/junit-platform-engine-1.11.4.jar"
                dependencies = []

                [[package]]
                id = "org.junit.platform:junit-platform-launcher"
                version = "1.11.4"
                source = "maven-central"
                scope = "test"
                direct = false
                jar = "org/junit/platform/junit-platform-launcher/1.11.4/junit-platform-launcher-1.11.4.jar"
                dependencies = []

                [[package]]
                id = "org.junit.platform:junit-platform-reporting"
                version = "1.11.4"
                source = "maven-central"
                scope = "test"
                direct = false
                jar = "org/junit/platform/junit-platform-reporting/1.11.4/junit-platform-reporting-1.11.4.jar"
                dependencies = []

                [[package]]
                id = "org.opentest4j:opentest4j"
                version = "1.3.0"
                source = "maven-central"
                scope = "test"
                direct = false
                jar = "org/opentest4j/opentest4j/1.3.0/opentest4j-1.3.0.jar"
                dependencies = []
                """);
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

    static void createSymlink(Path link, Path target) throws IOException {
        Files.createDirectories(link.getParent());
        try {
            Files.createSymbolicLink(link, target);
        } catch (UnsupportedOperationException | IOException exception) {
            assumeTrue(false, "symbolic links are unavailable: " + exception.getMessage());
        }
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

    static final class CachingJdkChecker implements JdkChecker {
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

        int detectCalls() {
            return detectCalls;
        }

        int toolchainReads() {
            return toolchainReads;
        }
    }
}
