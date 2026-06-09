package com.zolt.quarkus;

import java.util.List;

public record QuarkusAnnotationLaunchRequest(
        QuarkusTestRunnerDescriptor descriptor,
        QuarkusAnnotationApi api,
        List<String> testClasses,
        List<String> jvmArguments,
        List<String> consoleArguments) {
    public QuarkusAnnotationLaunchRequest {
        if (descriptor == null) {
            throw new QuarkusAugmentationException("Quarkus annotation launch request requires a descriptor.");
        }
        if (api == null) {
            throw new QuarkusAugmentationException("Quarkus annotation launch request requires probed API metadata.");
        }
        if (testClasses == null || testClasses.isEmpty()) {
            throw new QuarkusAugmentationException("Quarkus annotation launch request requires test classes.");
        }
        if (jvmArguments == null) {
            throw new QuarkusAugmentationException("Quarkus annotation launch request requires JVM arguments.");
        }
        if (consoleArguments == null || consoleArguments.isEmpty()) {
            throw new QuarkusAugmentationException("Quarkus annotation launch request requires console arguments.");
        }
        testClasses = List.copyOf(testClasses);
        jvmArguments = List.copyOf(jvmArguments);
        consoleArguments = List.copyOf(consoleArguments);
    }
}
