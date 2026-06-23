package com.zolt.plan;

import com.zolt.project.PackageMode;
import com.zolt.project.ProjectConfig;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

final class SpringBootNativeProjectDetector {
    private static final String SPRING_BOOT_GROUP = "org.springframework.boot";
    private static final String MICRONAUT_GROUP = "io.micronaut";

    private SpringBootNativeProjectDetector() {
    }

    static Optional<String> springBootVersion(ProjectConfig config) {
        String platformVersion = config.platforms().get("org.springframework.boot:spring-boot-dependencies");
        if (platformVersion != null && !platformVersion.isBlank()) {
            return Optional.of(platformVersion);
        }
        return Stream.of(
                        config.apiDependencies(),
                        config.dependencies(),
                        config.runtimeDependencies(),
                        config.providedDependencies(),
                        config.devDependencies(),
                        config.testDependencies(),
                        config.annotationProcessors(),
                        config.testAnnotationProcessors())
                .flatMap(map -> map.entrySet().stream())
                .filter(entry -> entry.getKey().startsWith(SPRING_BOOT_GROUP + ":"))
                .map(Map.Entry::getValue)
                .filter(value -> value != null && !value.isBlank())
                .findFirst();
    }

    static boolean springBootProject(ProjectConfig config) {
        PackageMode packageMode = config.packageSettings().mode();
        if (packageMode == PackageMode.SPRING_BOOT || packageMode == PackageMode.SPRING_BOOT_WAR) {
            return true;
        }
        if (containsCoordinate(config.platforms().keySet(), SPRING_BOOT_GROUP)) {
            return true;
        }
        return containsCoordinate(config.apiDependencies().keySet(), SPRING_BOOT_GROUP)
                || containsCoordinate(config.managedApiDependencies(), SPRING_BOOT_GROUP)
                || containsCoordinate(config.dependencies().keySet(), SPRING_BOOT_GROUP)
                || containsCoordinate(config.managedDependencies(), SPRING_BOOT_GROUP)
                || containsCoordinate(config.runtimeDependencies().keySet(), SPRING_BOOT_GROUP)
                || containsCoordinate(config.managedRuntimeDependencies(), SPRING_BOOT_GROUP)
                || containsCoordinate(config.providedDependencies().keySet(), SPRING_BOOT_GROUP)
                || containsCoordinate(config.managedProvidedDependencies(), SPRING_BOOT_GROUP)
                || containsCoordinate(config.devDependencies().keySet(), SPRING_BOOT_GROUP)
                || containsCoordinate(config.managedDevDependencies(), SPRING_BOOT_GROUP)
                || containsCoordinate(config.testDependencies().keySet(), SPRING_BOOT_GROUP)
                || containsCoordinate(config.managedTestDependencies(), SPRING_BOOT_GROUP)
                || containsCoordinate(config.annotationProcessors().keySet(), SPRING_BOOT_GROUP)
                || containsCoordinate(config.managedAnnotationProcessors(), SPRING_BOOT_GROUP)
                || containsCoordinate(config.testAnnotationProcessors().keySet(), SPRING_BOOT_GROUP)
                || containsCoordinate(config.managedTestAnnotationProcessors(), SPRING_BOOT_GROUP);
    }

    static boolean micronautProject(ProjectConfig config) {
        return containsCoordinate(config.platforms().keySet(), MICRONAUT_GROUP)
                || containsCoordinate(config.apiDependencies().keySet(), MICRONAUT_GROUP)
                || containsCoordinate(config.managedApiDependencies(), MICRONAUT_GROUP)
                || containsCoordinate(config.dependencies().keySet(), MICRONAUT_GROUP)
                || containsCoordinate(config.managedDependencies(), MICRONAUT_GROUP)
                || containsCoordinate(config.runtimeDependencies().keySet(), MICRONAUT_GROUP)
                || containsCoordinate(config.managedRuntimeDependencies(), MICRONAUT_GROUP)
                || containsCoordinate(config.providedDependencies().keySet(), MICRONAUT_GROUP)
                || containsCoordinate(config.managedProvidedDependencies(), MICRONAUT_GROUP)
                || containsCoordinate(config.devDependencies().keySet(), MICRONAUT_GROUP)
                || containsCoordinate(config.managedDevDependencies(), MICRONAUT_GROUP)
                || containsCoordinate(config.testDependencies().keySet(), MICRONAUT_GROUP)
                || containsCoordinate(config.managedTestDependencies(), MICRONAUT_GROUP)
                || containsCoordinate(config.annotationProcessors().keySet(), MICRONAUT_GROUP)
                || containsCoordinate(config.managedAnnotationProcessors(), MICRONAUT_GROUP)
                || containsCoordinate(config.testAnnotationProcessors().keySet(), MICRONAUT_GROUP)
                || containsCoordinate(config.managedTestAnnotationProcessors(), MICRONAUT_GROUP);
    }

    static boolean quarkusProject(ProjectConfig config) {
        return config.packageSettings().mode() == PackageMode.QUARKUS || config.frameworkSettings().quarkus().enabled();
    }

    private static boolean containsCoordinate(Iterable<String> coordinates, String group) {
        for (String coordinate : coordinates) {
            if (coordinate != null && coordinate.toLowerCase(Locale.ROOT).startsWith(group + ":")) {
                return true;
            }
        }
        return false;
    }
}
