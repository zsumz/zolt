package com.zolt.classpath;

import com.zolt.dependency.DependencyScope;
import java.util.ArrayList;
import java.util.List;

public final class ClasspathLanePolicy {
    private ClasspathLanePolicy() {
    }

    public static List<String> lanes(DependencyScope scope) {
        ArrayList<String> lanes = new ArrayList<>();
        if (scope.entersMainCompileClasspath()) {
            lanes.add("compile");
        }
        if (scope.entersMainRuntimeClasspath()) {
            lanes.add("runtime");
        }
        if (entersTestRuntimeClasspath(scope)) {
            lanes.add("test");
        }
        if (scope.entersMainProcessorClasspath()) {
            lanes.add("processor");
        }
        if (scope.entersTestProcessorClasspath()) {
            lanes.add("test-processor");
        }
        if (scope == DependencyScope.QUARKUS_DEPLOYMENT) {
            lanes.add("quarkus-deployment");
        }
        if (scope == DependencyScope.TOOL_OPENAPI) {
            lanes.add("tool-openapi");
        }
        if (scope == DependencyScope.TOOL_PROTOBUF) {
            lanes.add("tool-protobuf");
        }
        if (scope == DependencyScope.TOOL_COVERAGE) {
            lanes.add("tool-coverage");
        }
        return List.copyOf(lanes);
    }

    public static boolean entersTestRuntimeClasspath(DependencyScope scope) {
        return scope.entersMainRuntimeClasspath() || scope.entersTestClasspath();
    }

    public static String disposition(DependencyScope scope) {
        return switch (scope) {
            case COMPILE, RUNTIME -> "package-default";
            case PROVIDED -> "provided-container";
            case DEV -> "development-only";
            case TEST -> "test-only";
            case PROCESSOR, TEST_PROCESSOR -> "processor-only";
            case QUARKUS_DEPLOYMENT -> "quarkus-augmentation-only";
            case TOOL_OPENAPI -> "openapi-generator-tooling-only";
            case TOOL_PROTOBUF -> "protobuf-generator-tooling-only";
            case TOOL_COVERAGE -> "coverage-tooling-only";
        };
    }
}
