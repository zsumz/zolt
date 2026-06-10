package com.zolt.quarkus;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class QuarkusAnnotationLaunchRequestFactory {
    private static final String JBOSS_LOG_MANAGER_PROPERTY =
            "-Djava.util.logging.manager=org.jboss.logmanager.LogManager";
    private static final String RUNNER_MAIN_CLASS = QuarkusAnnotationProgrammaticRunner.MAIN_CLASS;

    private final QuarkusAnnotationLauncherClasspathPlanner launcherClasspathPlanner;

    public QuarkusAnnotationLaunchRequestFactory() {
        this(new QuarkusAnnotationLauncherClasspathPlanner());
    }

    QuarkusAnnotationLaunchRequestFactory(QuarkusAnnotationLauncherClasspathPlanner launcherClasspathPlanner) {
        if (launcherClasspathPlanner == null) {
            throw new QuarkusAugmentationException(
                    "Quarkus annotation launch request requires a launcher classpath planner.");
        }
        this.launcherClasspathPlanner = launcherClasspathPlanner;
    }

    public QuarkusAnnotationLaunchRequest create(QuarkusTestWorkerPlan plan, QuarkusAnnotationApi api) {
        if (plan == null) {
            throw new QuarkusAugmentationException("Quarkus annotation launch request requires a worker plan.");
        }
        if (api == null) {
            throw new QuarkusAugmentationException("Quarkus annotation launch request requires probed API metadata.");
        }
        List<String> testClasses = testClasses(plan.unsupportedTests());
        QuarkusTestRunnerDescriptor descriptor = plan.descriptor();
        QuarkusAnnotationLauncherClasspathPlan classpathPlan = launcherClasspathPlanner.plan(descriptor);
        return new QuarkusAnnotationLaunchRequest(
                descriptor,
                api,
                testClasses,
                jvmArguments(descriptor, testClasses),
                classpathPlan.launcherClasspath(),
                runnerArguments(testClasses));
    }

    private static List<String> testClasses(List<QuarkusUnsupportedTest> tests) {
        if (tests == null || tests.isEmpty()) {
            throw new QuarkusAugmentationException(
                    "Quarkus annotation launch request requires at least one Quarkus-specific test class.");
        }
        return tests.stream()
                .map(QuarkusAnnotationLaunchRequestFactory::testClassName)
                .distinct()
                .sorted(Comparator.naturalOrder())
                .toList();
    }

    private static String testClassName(QuarkusUnsupportedTest test) {
        if (test == null) {
            throw new QuarkusAugmentationException("Quarkus annotation launch request cannot include a null test.");
        }
        String relativePath = test.relativePath().toString().replace('\\', '/');
        if (!relativePath.endsWith(".class")) {
            throw new QuarkusAugmentationException(
                    "Quarkus annotation launch request expected a compiled .class file but found "
                            + test.relativePath()
                            + ".");
        }
        return relativePath.substring(0, relativePath.length() - ".class".length()).replace('/', '.');
    }

    private static List<String> jvmArguments(QuarkusTestRunnerDescriptor descriptor, List<String> testClasses) {
        List<String> arguments = new ArrayList<>();
        arguments.add("-Duser.dir=" + descriptor.projectDirectory());
        arguments.add("-D"
                + QuarkusTestApplicationModelService.SERIALIZED_TEST_MODEL_PROPERTY
                + "="
                + descriptor.serializedApplicationModel());
        arguments.add("-D"
                + QuarkusAnnotationProgrammaticRunner.TEST_OUTPUT_DIRECTORY_PROPERTY
                + "="
                + descriptor.testOutputDirectory());
        arguments.add("-Dquarkus.arc.unremovable-types=" + String.join(",", testClasses));
        if (descriptor.jbossLogManagerPresent()) {
            arguments.add(JBOSS_LOG_MANAGER_PROPERTY);
        }
        return List.copyOf(arguments);
    }

    private static List<String> runnerArguments(List<String> testClasses) {
        List<String> arguments = new ArrayList<>();
        arguments.add(RUNNER_MAIN_CLASS);
        arguments.addAll(testClasses);
        return List.copyOf(arguments);
    }
}
