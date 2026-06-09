package com.zolt.quarkus;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.StringJoiner;

public final class QuarkusAnnotationLaunchRequestFactory {
    private static final String JBOSS_LOG_MANAGER_PROPERTY =
            "-Djava.util.logging.manager=org.jboss.logmanager.LogManager";
    private static final String CONSOLE_MAIN_CLASS = "org.junit.platform.console.ConsoleLauncher";

    private final String pathSeparator;
    private final QuarkusAnnotationLauncherClasspathPlanner launcherClasspathPlanner;

    public QuarkusAnnotationLaunchRequestFactory() {
        this(java.io.File.pathSeparator, new QuarkusAnnotationLauncherClasspathPlanner());
    }

    QuarkusAnnotationLaunchRequestFactory(String pathSeparator) {
        this(pathSeparator, new QuarkusAnnotationLauncherClasspathPlanner());
    }

    QuarkusAnnotationLaunchRequestFactory(
            String pathSeparator,
            QuarkusAnnotationLauncherClasspathPlanner launcherClasspathPlanner) {
        if (pathSeparator == null || pathSeparator.isBlank()) {
            throw new QuarkusAugmentationException("Quarkus annotation launch request path separator is required.");
        }
        if (launcherClasspathPlanner == null) {
            throw new QuarkusAugmentationException(
                    "Quarkus annotation launch request requires a launcher classpath planner.");
        }
        this.pathSeparator = pathSeparator;
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
                jvmArguments(descriptor),
                classpathPlan.launcherClasspath(),
                consoleArguments(classpathPlan.junitDiscoveryClasspath(), testClasses));
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

    private static List<String> jvmArguments(QuarkusTestRunnerDescriptor descriptor) {
        List<String> arguments = new ArrayList<>();
        arguments.add("-Duser.dir=" + descriptor.projectDirectory());
        arguments.add("-D"
                + QuarkusTestApplicationModelService.SERIALIZED_TEST_MODEL_PROPERTY
                + "="
                + descriptor.serializedApplicationModel());
        if (descriptor.jbossLogManagerPresent()) {
            arguments.add(JBOSS_LOG_MANAGER_PROPERTY);
        }
        return List.copyOf(arguments);
    }

    private List<String> consoleArguments(List<Path> junitDiscoveryClasspath, List<String> testClasses) {
        String classpath = joined(junitDiscoveryClasspath);
        List<String> arguments = new ArrayList<>();
        arguments.add(CONSOLE_MAIN_CLASS);
        arguments.add("execute");
        arguments.add("--disable-banner");
        arguments.add("--class-path");
        arguments.add(classpath);
        for (String testClass : testClasses) {
            arguments.add("--select-class");
            arguments.add(testClass);
        }
        arguments.add("--details");
        arguments.add("summary");
        return List.copyOf(arguments);
    }

    private String joined(List<Path> classpath) {
        StringJoiner joiner = new StringJoiner(pathSeparator);
        for (Path entry : classpath) {
            joiner.add(entry.normalize().toString());
        }
        return joiner.toString();
    }
}
