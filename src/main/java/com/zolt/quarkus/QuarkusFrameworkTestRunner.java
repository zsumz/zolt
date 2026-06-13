package com.zolt.quarkus;

import com.zolt.build.TestRunException;
import com.zolt.build.TestJvmArguments;
import com.zolt.build.TestSelection;
import com.zolt.framework.FrameworkTestRunRequest;
import com.zolt.framework.FrameworkTestRunResult;
import com.zolt.framework.FrameworkTestRunner;
import com.zolt.project.ProjectConfig;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

public final class QuarkusFrameworkTestRunner implements FrameworkTestRunner {
    private final QuarkusTestApplicationModelWriter applicationModelWriter;
    private final QuarkusTestDescriptorWriter descriptorWriter;
    private final QuarkusTestPlanner testPlanner;
    private final Supplier<List<Path>> workerClasspath;
    private final QuarkusTestWorkerRunner workerRunner;

    public QuarkusFrameworkTestRunner() {
        this(
                new QuarkusTestApplicationModelService()::writeIfEnabled,
                new QuarkusTestRunnerDescriptorWriter()::write,
                new QuarkusTestPlanService()::plan,
                QuarkusFrameworkTestRunner::currentWorkerClasspath,
                (javaExecutable, classpath, descriptor) ->
                        new QuarkusTestWorkerLauncher(javaExecutable, classpath).run(descriptor));
    }

    QuarkusFrameworkTestRunner(
            QuarkusTestApplicationModelWriter applicationModelWriter,
            QuarkusTestDescriptorWriter descriptorWriter,
            Supplier<List<Path>> workerClasspath,
            QuarkusTestWorkerRunner workerRunner) {
        this(
                applicationModelWriter,
                descriptorWriter,
                new QuarkusTestPlanService()::plan,
                workerClasspath,
                workerRunner);
    }

    QuarkusFrameworkTestRunner(
            QuarkusTestApplicationModelWriter applicationModelWriter,
            QuarkusTestDescriptorWriter descriptorWriter,
            QuarkusTestPlanner testPlanner,
            Supplier<List<Path>> workerClasspath,
            QuarkusTestWorkerRunner workerRunner) {
        this.applicationModelWriter = applicationModelWriter;
        this.descriptorWriter = descriptorWriter;
        this.testPlanner = testPlanner;
        this.workerClasspath = workerClasspath;
        this.workerRunner = workerRunner;
    }

    @Override
    public Optional<FrameworkTestRunResult> runIfEnabled(FrameworkTestRunRequest request) {
        if (!request.config().frameworkSettings().quarkus().enabled()) {
            return Optional.empty();
        }
        Optional<Path> serializedApplicationModel = writeApplicationModel(request);
        Path modelPath = serializedApplicationModel.orElseThrow(() -> new TestRunException(
                "Could not prepare Quarkus test runner descriptor because the serialized application model was not written. "
                        + "Run `zolt build`, then run `zolt test` again."));
        QuarkusTestRunnerDescriptor descriptor = writeDescriptor(request, modelPath);
        failOnUnsupportedTests(request, descriptor.supportsQuarkusTestAnnotations());
        List<Path> classpath = workerClasspath.get();
        String output = runWorker(request.javaExecutable(), classpath, descriptor);
        failOnHiddenBootstrapFailure(output);
        return Optional.of(new FrameworkTestRunResult(
                output,
                descriptor.supportsQuarkusTestAnnotations(),
                classpath.size(),
                1));
    }

    private Optional<Path> writeApplicationModel(FrameworkTestRunRequest request) {
        try {
            return applicationModelWriter.write(request.projectDirectory(), request.config());
        } catch (QuarkusAugmentationException exception) {
            throw new TestRunException(
                    "Could not prepare Quarkus test application model. "
                            + "Run `zolt resolve`, then run `zolt test` again. "
                            + exception.getMessage(),
                    exception);
        }
    }

    private QuarkusTestRunnerDescriptor writeDescriptor(FrameworkTestRunRequest request, Path modelPath) {
        try {
            return descriptorWriter.write(new QuarkusTestRunnerRequest(
                    request.projectDirectory(),
                    request.mainOutputDirectory(),
                    request.testOutputDirectory(),
                    modelPath,
                    request.projectDirectory().resolve("target/quarkus/zolt-bootstrap.properties"),
                    request.testRuntimeClasspath(),
                    request.testRuntimeClasspath().stream().anyMatch(QuarkusFrameworkTestRunner::isJbossLogManagerJar),
                    testSelection(request),
                    new TestJvmArguments(request.jvmArguments()),
                    request.environment()));
        } catch (QuarkusAugmentationException exception) {
            throw new TestRunException(
                    "Could not write Quarkus test runner descriptor. "
                            + "Clean target/quarkus, run `zolt build`, then run `zolt test` again. "
                            + exception.getMessage(),
                    exception);
        }
    }

    private String runWorker(
            Path javaExecutable,
            List<Path> classpath,
            QuarkusTestRunnerDescriptor descriptor) {
        try {
            return workerRunner.run(javaExecutable, classpath, descriptor);
        } catch (QuarkusAugmentationException exception) {
            throw new TestRunException(exception.getMessage(), exception);
        }
    }

    private void failOnUnsupportedTests(
            FrameworkTestRunRequest request,
            boolean supportsQuarkusTestAnnotations) {
        if (supportsQuarkusTestAnnotations) {
            return;
        }
        try {
            QuarkusTestPlan plan = testPlanner.plan(request.projectDirectory(), request.config());
            if (plan.hasUnsupportedTests()) {
                QuarkusUnsupportedTest firstUnsupportedTest = plan.unsupportedTests().getFirst();
                throw new TestRunException(
                        "Quarkus-specific `" + firstUnsupportedTest.annotationName()
                                + "` execution is not supported by Zolt's current test runner. "
                                + "Use plain JUnit tests for now, or remove `" + firstUnsupportedTest.annotationName()
                                + "` until Zolt's dedicated "
                                + "Quarkus test runner is implemented. Found "
                                + firstUnsupportedTest.relativePath()
                                + ".");
            }
        } catch (QuarkusPlanException exception) {
            throw new TestRunException(exception.getMessage(), exception);
        }
    }

    static void failOnHiddenBootstrapFailure(String output) {
        if (output == null || output.isBlank()) {
            return;
        }
        if (!hiddenBootstrapFailure(output)) {
            return;
        }
        throw new TestRunException(
                "Quarkus test bootstrap failed while JUnit Platform reported success. "
                        + "Zolt supports an early Quarkus test runner path, but this project hit an unsupported "
                        + "Quarkus test bootstrap shape. Use plain JUnit tests for now, or simplify `@QuarkusTest` "
                        + "usage until Zolt's dedicated Quarkus test runner is expanded.\n"
                        + output.stripTrailing());
    }

    private static boolean hiddenBootstrapFailure(String output) {
        return output.contains("io.quarkus.test.junit")
                && (output.contains("io.quarkus.bootstrap.BootstrapException")
                        || output.contains("ClassCastException: class io.quarkus.builder.BuildChainBuilder")
                        || output.contains("NullPointerException"));
    }

    private static boolean isJbossLogManagerJar(Path path) {
        String name = path.getFileName() == null ? "" : path.getFileName().toString();
        return name.startsWith("jboss-logmanager-") && name.endsWith(".jar");
    }

    private static List<Path> currentWorkerClasspath() {
        String classpath = System.getProperty("java.class.path", "");
        List<Path> entries = Arrays.stream(classpath.split(java.io.File.pathSeparator))
                .filter(entry -> !entry.isBlank())
                .map(Path::of)
                .map(path -> path.toAbsolutePath().normalize())
                .toList();
        if (entries.isEmpty()) {
            throw new TestRunException(
                    "Could not determine Zolt worker classpath for test execution. "
                            + "Run zolt test from the packaged launcher or check java.class.path.");
        }
        return entries;
    }

    private static TestSelection testSelection(FrameworkTestRunRequest request) {
        return new TestSelection(
                request.testSelection().classSelectors(),
                request.testSelection().methodSelectors().stream()
                        .map(method -> new TestSelection.MethodSelector(
                                method.className(),
                                method.methodName()))
                        .toList(),
                request.testSelection().classNamePatterns(),
                request.testSelection().includedTags(),
                request.testSelection().excludedTags());
    }

    @FunctionalInterface
    interface QuarkusTestApplicationModelWriter {
        Optional<Path> write(Path projectDirectory, ProjectConfig config);
    }

    @FunctionalInterface
    interface QuarkusTestDescriptorWriter {
        QuarkusTestRunnerDescriptor write(QuarkusTestRunnerRequest request);
    }

    @FunctionalInterface
    interface QuarkusTestPlanner {
        QuarkusTestPlan plan(Path projectDirectory, ProjectConfig config);
    }

    @FunctionalInterface
    interface QuarkusTestWorkerRunner {
        String run(Path javaExecutable, List<Path> workerClasspath, QuarkusTestRunnerDescriptor descriptor);
    }
}
