package com.zolt.quarkus.annotation;

import com.zolt.quarkus.QuarkusAugmentationException;
import com.zolt.quarkus.annotation.diagnostic.QuarkusAnnotationClasspathSplitDiagnostic;
import com.zolt.quarkus.annotation.diagnostic.QuarkusAnnotationDiagnosticFiles;
import com.zolt.quarkus.annotation.diagnostic.QuarkusAnnotationWorkerOutputDiagnoser;
import com.zolt.quarkus.annotation.launcher.QuarkusAnnotationJvmRunner;
import com.zolt.quarkus.annotation.launcher.QuarkusAnnotationLaunchRequest;
import com.zolt.quarkus.annotation.launcher.QuarkusAnnotationLaunchRequestFactory;
import com.zolt.quarkus.testworker.descriptor.QuarkusTestRunnerDescriptor;
import com.zolt.quarkus.testworker.QuarkusTestWorkerPlan;

public final class QuarkusAnnotationWorkerRunner {
    private final ApiProbe apiProbe;
    private final LaunchRequestFactory launchRequestFactory;
    private final LaunchRunner launchRunner;
    private final QuarkusAnnotationWorkerOutputDiagnoser outputDiagnoser;
    private final TestIndexWriter testIndexWriter;

    public QuarkusAnnotationWorkerRunner() {
        this(
                new QuarkusAnnotationApiProbe()::probe,
                new QuarkusAnnotationLaunchRequestFactory()::create,
                new QuarkusAnnotationJvmRunner()::run,
                new QuarkusAnnotationClasspathSplitDiagnostic(),
                new ReflectiveTestIndexWriter());
    }

    QuarkusAnnotationWorkerRunner(ApiProbe apiProbe) {
        this(
                apiProbe,
                new QuarkusAnnotationLaunchRequestFactory()::create,
                new QuarkusAnnotationJvmRunner()::run,
                new QuarkusAnnotationClasspathSplitDiagnostic(),
                request -> {
                });
    }

    QuarkusAnnotationWorkerRunner(
            ApiProbe apiProbe,
            LaunchRequestFactory launchRequestFactory) {
        this(apiProbe, launchRequestFactory, new QuarkusAnnotationJvmRunner()::run);
    }

    QuarkusAnnotationWorkerRunner(
            ApiProbe apiProbe,
            LaunchRequestFactory launchRequestFactory,
            LaunchRunner launchRunner) {
        this(
                apiProbe,
                launchRequestFactory,
                launchRunner,
                new QuarkusAnnotationClasspathSplitDiagnostic(),
                request -> {
                });
    }

    QuarkusAnnotationWorkerRunner(
            ApiProbe apiProbe,
            LaunchRequestFactory launchRequestFactory,
            LaunchRunner launchRunner,
            QuarkusAnnotationClasspathSplitDiagnostic classpathSplitDiagnostic) {
        this(
                apiProbe,
                launchRequestFactory,
                launchRunner,
                classpathSplitDiagnostic,
                request -> {
                });
    }

    QuarkusAnnotationWorkerRunner(
            ApiProbe apiProbe,
            LaunchRequestFactory launchRequestFactory,
            LaunchRunner launchRunner,
            QuarkusAnnotationClasspathSplitDiagnostic classpathSplitDiagnostic,
            TestIndexWriter testIndexWriter) {
        this(
                apiProbe,
                launchRequestFactory,
                launchRunner,
                new QuarkusAnnotationWorkerOutputDiagnoser(classpathSplitDiagnostic),
                testIndexWriter);
    }

    private QuarkusAnnotationWorkerRunner(
            ApiProbe apiProbe,
            LaunchRequestFactory launchRequestFactory,
            LaunchRunner launchRunner,
            QuarkusAnnotationWorkerOutputDiagnoser outputDiagnoser,
            TestIndexWriter testIndexWriter) {
        if (apiProbe == null) {
            throw new QuarkusAugmentationException("Quarkus annotation worker API probe is required.");
        }
        if (launchRequestFactory == null) {
            throw new QuarkusAugmentationException("Quarkus annotation worker launch request factory is required.");
        }
        if (launchRunner == null) {
            throw new QuarkusAugmentationException("Quarkus annotation worker launch runner is required.");
        }
        if (outputDiagnoser == null) {
            throw new QuarkusAugmentationException(
                    "Quarkus annotation worker output diagnoser is required.");
        }
        if (testIndexWriter == null) {
            throw new QuarkusAugmentationException(
                    "Quarkus annotation worker test index writer is required.");
        }
        this.apiProbe = apiProbe;
        this.launchRequestFactory = launchRequestFactory;
        this.launchRunner = launchRunner;
        this.outputDiagnoser = outputDiagnoser;
        this.testIndexWriter = testIndexWriter;
    }

    public Result run(QuarkusTestWorkerPlan plan) {
        if (plan == null) {
            throw new QuarkusAugmentationException("Quarkus annotation worker plan is required.");
        }
        QuarkusAnnotationApi api = apiProbe.probe(plan.descriptor());
        QuarkusAnnotationLaunchRequest launchRequest = launchRequestFactory.create(plan, api);
        testIndexWriter.write(launchRequest);
        QuarkusAnnotationDiagnosticFiles.reset(launchRequest);
        QuarkusAnnotationJvmRunner.Result result = launchRunner.run(launchRequest);
        return new Result(result.exitCode(), outputDiagnoser.diagnose(launchRequest, result));
    }

    public record Result(int exitCode, String output) {
    }

    @FunctionalInterface
    interface ApiProbe {
        QuarkusAnnotationApi probe(QuarkusTestRunnerDescriptor descriptor);
    }

    @FunctionalInterface
    interface LaunchRequestFactory {
        QuarkusAnnotationLaunchRequest create(QuarkusTestWorkerPlan plan, QuarkusAnnotationApi api);
    }

    @FunctionalInterface
    interface LaunchRunner {
        QuarkusAnnotationJvmRunner.Result run(QuarkusAnnotationLaunchRequest request);
    }

    @FunctionalInterface
    interface TestIndexWriter {
        void write(QuarkusAnnotationLaunchRequest request);
    }

    private static final class ReflectiveTestIndexWriter implements TestIndexWriter {
        @Override
        public void write(QuarkusAnnotationLaunchRequest request) {
            if (request.testClasses().isEmpty()) {
                return;
            }
            try {
                java.nio.file.Path outputDirectory = request.descriptor()
                        .testOutputDirectory()
                        .toAbsolutePath()
                        .normalize();
                new QuarkusTestIndexWriter().write(outputDirectory, request.testClasses());
            } catch (ReflectiveOperationException | LinkageError exception) {
                throw new QuarkusAugmentationException(
                        "Could not write Quarkus test class index before annotation execution. "
                                + "Check that quarkus-test-common and Jandex are on the test runtime classpath.",
                        exception);
            }
        }
    }
}
