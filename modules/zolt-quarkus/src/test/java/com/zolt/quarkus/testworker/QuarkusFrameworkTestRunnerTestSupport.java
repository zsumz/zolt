package com.zolt.quarkus.testworker;

import com.zolt.framework.FrameworkTestRunRequest;
import com.zolt.framework.FrameworkTestSelection;
import com.zolt.project.BuildSettings;
import com.zolt.project.FrameworkSettings;
import com.zolt.project.NativeSettings;
import com.zolt.project.ProjectConfig;
import com.zolt.project.ProjectConfigs;
import com.zolt.project.ProjectMetadata;
import com.zolt.project.QuarkusPackageMode;
import com.zolt.project.QuarkusSettings;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.io.TempDir;

abstract class QuarkusFrameworkTestRunnerTestSupport {
    @TempDir
    protected Path projectDir;

    protected FrameworkTestRunRequest request(ProjectConfig config) {
        return new FrameworkTestRunRequest(
                projectDir,
                config,
                projectDir.resolve("target/classes"),
                projectDir.resolve("target/test-classes"),
                List.of(Path.of("/repo/jboss-logmanager-3.0.jar")),
                Path.of("/jdk/bin/java"),
                new FrameworkTestSelection(
                        List.of("com.example.FastTest"),
                        List.of(new FrameworkTestSelection.MethodSelector("com.example.FastTest", "runs")),
                        List.of("*Test"),
                        List.of("fast"),
                        List.of("slow")),
                List.of("-Ddemo=true"),
                Map.of("APP_ENV", "test"));
    }

    protected static QuarkusTestRunnerDescriptor descriptor(QuarkusTestRunnerRequest request) {
        return descriptor(request, QuarkusTestRunnerRequest.SUPPORTS_QUARKUS_TEST_ANNOTATIONS);
    }

    protected static QuarkusTestRunnerDescriptor descriptor(
            QuarkusTestRunnerRequest request,
            boolean supportsQuarkusTestAnnotations) {
        return new QuarkusTestRunnerDescriptor(
                request.projectDirectory().resolve("target/quarkus/zolt-test-bootstrap.properties"),
                request.projectDirectory().resolve("target/quarkus/zolt-test-classpath.txt"),
                request.projectDirectory(),
                request.mainOutputDirectory(),
                request.testOutputDirectory(),
                request.serializedApplicationModel(),
                request.bootstrapDescriptorFile(),
                QuarkusTestRunnerRequest.RUNNER_MODE,
                supportsQuarkusTestAnnotations,
                request.jbossLogManagerPresent(),
                request.testRuntimeClasspath(),
                request.testSelection(),
                request.jvmArguments(),
                request.environment());
    }

    protected static QuarkusFrameworkTestRunner runner(boolean supportsQuarkusTestAnnotations, String output) {
        return new QuarkusFrameworkTestRunner(
                (projectDirectory, config) ->
                        Optional.of(projectDirectory.resolve("target/quarkus/test-application-model.dat")),
                request -> descriptor(request, supportsQuarkusTestAnnotations),
                () -> List.of(Path.of("/zolt/zolt.jar")),
                (javaExecutable, workerClasspath, descriptor) -> output);
    }

    protected static ProjectConfig quarkusConfig() {
        return baseConfig().withFrameworkSettings(new FrameworkSettings(new QuarkusSettings(
                true,
                QuarkusPackageMode.FAST_JAR)));
    }

    protected static ProjectConfig disabledConfig() {
        return baseConfig();
    }

    private static ProjectConfig baseConfig() {
        return ProjectConfigs.withDirectDependencies(
                new ProjectMetadata("demo", "1.0.0", "com.example", "21", Optional.empty()),
                Map.of(),
                Map.of(),
                Map.of(),
                BuildSettings.defaults(),
                NativeSettings.defaults());
    }
}
