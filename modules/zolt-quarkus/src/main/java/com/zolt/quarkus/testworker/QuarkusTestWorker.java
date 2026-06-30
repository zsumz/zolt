package com.zolt.quarkus.testworker;

import com.zolt.quarkus.QuarkusAugmentationException;
import com.zolt.quarkus.QuarkusPlanException;
import com.zolt.quarkus.annotation.QuarkusAnnotationWorkerRunner;
import com.zolt.quarkus.testworker.descriptor.QuarkusTestRunnerDescriptor;
import com.zolt.quarkus.testworker.descriptor.QuarkusTestRunnerDescriptorReader;
import com.zolt.quarkus.testplan.QuarkusUnsupportedTest;
import java.io.PrintStream;
import java.nio.file.Path;

public final class QuarkusTestWorker {
    public static final String MAIN_CLASS = "com.zolt.quarkus.testworker.QuarkusTestWorker";

    private final DescriptorReader descriptorReader;
    private final PlanService planService;
    private final PlainJunitRunner plainJunitRunner;
    private final QuarkusAnnotationRunner quarkusAnnotationRunner;
    private final PrintStream out;
    private final PrintStream err;

    public QuarkusTestWorker() {
        this(
                new QuarkusTestRunnerDescriptorReader()::read,
                new QuarkusTestWorkerPlanService()::plan,
                new QuarkusPlainJunitWorkerRunner()::run,
                new QuarkusAnnotationWorkerRunner()::run,
                System.out,
                System.err);
    }

    QuarkusTestWorker(
            DescriptorReader descriptorReader,
            PlanService planService,
            PlainJunitRunner plainJunitRunner,
            QuarkusAnnotationRunner quarkusAnnotationRunner,
            PrintStream out,
            PrintStream err) {
        if (descriptorReader == null) {
            throw new QuarkusAugmentationException("Quarkus test worker descriptor reader is required.");
        }
        if (planService == null) {
            throw new QuarkusAugmentationException("Quarkus test worker plan service is required.");
        }
        if (plainJunitRunner == null) {
            throw new QuarkusAugmentationException("Quarkus test worker plain JUnit runner is required.");
        }
        if (quarkusAnnotationRunner == null) {
            throw new QuarkusAugmentationException("Quarkus test worker annotation runner is required.");
        }
        if (out == null) {
            throw new QuarkusAugmentationException("Quarkus test worker output stream is required.");
        }
        if (err == null) {
            throw new QuarkusAugmentationException("Quarkus test worker error stream is required.");
        }
        this.descriptorReader = descriptorReader;
        this.planService = planService;
        this.plainJunitRunner = plainJunitRunner;
        this.quarkusAnnotationRunner = quarkusAnnotationRunner;
        this.out = out;
        this.err = err;
    }

    public static void main(String[] args) {
        int exitCode = new QuarkusTestWorker().run(args);
        System.exit(exitCode);
    }

    int run(String[] args) {
        if (args == null || args.length != 1) {
            err.println("error: Quarkus test worker requires a test runner descriptor path.");
            return 2;
        }
        try {
            QuarkusTestRunnerDescriptor descriptor = descriptorReader.read(Path.of(args[0]));
            return run(planService.plan(descriptor));
        } catch (QuarkusAugmentationException | QuarkusPlanException exception) {
            err.println("error: " + exception.getMessage());
            return 1;
        }
    }

    private int run(QuarkusTestWorkerPlan plan) {
        QuarkusTestRunnerDescriptor descriptor = plan.descriptor();
        out.println("Quarkus test worker");
        out.println("Runner mode: " + descriptor.runnerMode());
        out.println("Status: " + plan.status().displayName());
        out.println("Descriptor: " + descriptor.descriptorFile());
        out.println("Quarkus annotation runner tests: " + plan.annotationRunnerTests().size());
        for (QuarkusUnsupportedTest test : plan.annotationRunnerTests()) {
            out.println("  " + test.relativePath() + " (" + test.annotationName() + ")");
        }
        out.println("Unsupported Quarkus tests: " + plan.blockedUnsupportedTests().size());
        for (QuarkusUnsupportedTest test : plan.blockedUnsupportedTests()) {
            out.println("  " + test.relativePath() + " (" + test.annotationName() + ")");
        }
        if (plan.plainJunitReady()) {
            QuarkusPlainJunitWorkerRunner.Result result = plainJunitRunner.run(descriptor);
            out.print(result.output());
            return result.exitCode();
        }
        if (plan.quarkusTestRunnerSelected()) {
            QuarkusAnnotationWorkerRunner.Result result = quarkusAnnotationRunner.run(plan);
            out.print(result.output());
            return result.exitCode();
        }
        err.println(errorMessage(plan));
        return 2;
    }

    private static String errorMessage(QuarkusTestWorkerPlan plan) {
        return switch (plan.status()) {
            case BLOCKED_UNSUPPORTED_QUARKUS_TESTS ->
                    "error: This project uses Quarkus test resource, profile, integration, or main annotations that are not supported "
                            + "by Zolt's Quarkus test worker yet. Remove those annotations or use the supported "
                            + "direct `@QuarkusTest` fixture shape until Zolt has dedicated evidence for broader "
                            + "Quarkus test modes.";
            case QUARKUS_TEST_ANNOTATIONS_DISABLED ->
                    "error: This descriptor does not enable Zolt's Quarkus annotation runner. "
                            + "Run `zolt test` again to refresh "
                            + plan.descriptor().descriptorFile()
                            + ", or use plain JUnit tests until the descriptor can be regenerated.";
            case MISSING_JUNIT_CONSOLE ->
                    "error: Quarkus test worker could not find JUnit Platform Console on the test runtime classpath. "
                            + "Run `zolt resolve`, then run `zolt test` again.";
            case UNSUPPORTED_RUNNER_MODE ->
                    "error: Quarkus test worker does not support runner mode `"
                            + plan.descriptor().runnerMode()
                            + "`. Run `zolt test` again to refresh "
                            + plan.descriptor().descriptorFile()
                            + ".";
            case PLAIN_JUNIT_READY ->
                    "error: Dedicated Quarkus test worker execution is not implemented yet. "
                            + "Run `zolt test` for the current plain JUnit path until Zolt owns Quarkus JUnit discovery "
                            + "and launcher/session listeners.";
            case QUARKUS_TEST_RUNNER_SELECTED ->
                    "error: Quarkus annotation test execution was selected but did not run. "
                            + "Run `zolt test` again or inspect the Quarkus test worker output.";
        };
    }

    @FunctionalInterface
    interface DescriptorReader {
        QuarkusTestRunnerDescriptor read(Path descriptorFile);
    }

    @FunctionalInterface
    interface PlanService {
        QuarkusTestWorkerPlan plan(QuarkusTestRunnerDescriptor descriptor);
    }

    @FunctionalInterface
    interface PlainJunitRunner {
        QuarkusPlainJunitWorkerRunner.Result run(QuarkusTestRunnerDescriptor descriptor);
    }

    @FunctionalInterface
    interface QuarkusAnnotationRunner {
        QuarkusAnnotationWorkerRunner.Result run(QuarkusTestWorkerPlan plan);
    }
}
