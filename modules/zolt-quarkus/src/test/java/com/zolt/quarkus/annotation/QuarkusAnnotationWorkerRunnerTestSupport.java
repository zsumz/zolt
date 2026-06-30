package com.zolt.quarkus.annotation;

import com.zolt.quarkus.annotation.launcher.QuarkusAnnotationJvmRunner;
import com.zolt.quarkus.annotation.launcher.QuarkusAnnotationLaunchRequest;
import com.zolt.quarkus.testworker.descriptor.QuarkusTestRunnerDescriptor;
import com.zolt.quarkus.testworker.QuarkusTestRunnerRequest;
import com.zolt.quarkus.testworker.QuarkusTestWorkerPlan;
import com.zolt.quarkus.testworker.QuarkusTestWorkerPlanStatus;
import java.nio.file.Path;
import java.util.List;

abstract class QuarkusAnnotationWorkerRunnerTestSupport {
    static QuarkusAnnotationWorkerRunner.Result diagnosticResult(String output) {
        return diagnosticResult(1, output);
    }

    static QuarkusAnnotationWorkerRunner.Result diagnosticResult(int exitCode, String output) {
        return new QuarkusAnnotationWorkerRunner(
                        descriptor -> api(),
                        (plan, api) -> new QuarkusAnnotationLaunchRequest(
                                plan.descriptor(),
                                api,
                                List.of("com.example.HttpTest"),
                                List.of("-Duser.dir=/repo"),
                                List.of(Path.of("/cache/junit-platform-console.jar")),
                                List.of("org.junit.platform.console.ConsoleLauncher")),
                        request -> new QuarkusAnnotationJvmRunner.Result(exitCode, output))
                .run(new QuarkusTestWorkerPlan(
                        descriptor(true),
                        QuarkusTestWorkerPlanStatus.QUARKUS_TEST_RUNNER_SELECTED,
                        List.of()));
    }

    static QuarkusTestRunnerDescriptor descriptor(boolean supportsQuarkusTestAnnotations) {
        return new QuarkusTestRunnerDescriptor(
                Path.of("/repo/target/quarkus/zolt-test-bootstrap.properties"),
                Path.of("/repo/target/quarkus/test-runtime-classpath.txt"),
                Path.of("/repo"),
                Path.of("/repo/target/classes"),
                Path.of("/repo/target/test-classes"),
                Path.of("/repo/target/quarkus/test-application-model.dat"),
                Path.of("/repo/target/quarkus/zolt-bootstrap.properties"),
                QuarkusTestRunnerRequest.RUNNER_MODE,
                supportsQuarkusTestAnnotations,
                true,
                List.of(
                        Path.of("/repo/target/test-classes"),
                        Path.of("/repo/target/classes"),
                        Path.of("/cache/junit-platform-console.jar")));
    }

    static QuarkusAnnotationApi api() {
        return new QuarkusAnnotationApi(
                "io.quarkus.test.junit.QuarkusTestExtension",
                "io.quarkus.test.junit.QuarkusTestProfile",
                "io.quarkus.test.junit.launcher.CustomLauncherInterceptor",
                List.of("io.quarkus.test.junit.launcher.JarLauncherProvider"));
    }
}
