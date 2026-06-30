package com.zolt.build.springboot;

import com.zolt.build.nativeimage.NativeImageException;
import com.zolt.error.ActionableError;
import com.zolt.project.ProjectConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class SpringBootNativeBoundaryDiagnostics {
    private static final String SPRING_CLOUD_GROUP = "org.springframework.cloud";
    private static final List<BoundarySignal> BOUNDARY_SIGNALS = List.of(
            new BoundarySignal(
                    SPRING_CLOUD_GROUP,
                    "Spring Cloud native applications",
                    "Use the JVM Spring Boot path or add a dedicated Spring Cloud native fixture before enabling native."),
            new BoundarySignal(
                    "org.postgresql:postgresql",
                    "external database native topologies",
                    "Use the proven Spring JDBC/H2 native fixture row or keep external database projects on the JVM Spring Boot path."),
            new BoundarySignal(
                    "com.mysql:mysql-connector-j",
                    "external database native topologies",
                    "Use the proven Spring JDBC/H2 native fixture row or keep external database projects on the JVM Spring Boot path."),
            new BoundarySignal(
                    "org.mariadb.jdbc:mariadb-java-client",
                    "external database native topologies",
                    "Use the proven Spring JDBC/H2 native fixture row or keep external database projects on the JVM Spring Boot path."),
            new BoundarySignal(
                    "com.microsoft.sqlserver:mssql-jdbc",
                    "external database native topologies",
                    "Use the proven Spring JDBC/H2 native fixture row or keep external database projects on the JVM Spring Boot path."));

    private SpringBootNativeBoundaryDiagnostics() {
    }

    public static void rejectUnsupportedEcosystem(ProjectConfig config) {
        boundarySignal(config).ifPresent(signal -> {
            throw new NativeImageException(ActionableError.of(
                    signal.area()
                            + " are not part of Zolt's proven Spring Boot native fixture family yet. "
                            + "Zolt currently proves Spring Boot 3.3 Java 21 native rows for WebMVC, Actuator, "
                            + "WebMVC contract behavior, and Spring JDBC/H2 data access.",
                    signal.nextStep()));
        });
    }

    private static Optional<BoundarySignal> boundarySignal(ProjectConfig config) {
        List<String> coordinates = projectCoordinates(config);
        return BOUNDARY_SIGNALS.stream()
                .filter(signal -> coordinates.stream().anyMatch(signal::matches))
                .findFirst();
    }

    private static List<String> projectCoordinates(ProjectConfig config) {
        List<String> coordinates = new ArrayList<>();
        addCoordinates(coordinates, config.platforms().keySet());
        addCoordinates(coordinates, config.apiDependencies().keySet());
        addCoordinates(coordinates, config.managedApiDependencies());
        addCoordinates(coordinates, config.dependencies().keySet());
        addCoordinates(coordinates, config.managedDependencies());
        addCoordinates(coordinates, config.runtimeDependencies().keySet());
        addCoordinates(coordinates, config.managedRuntimeDependencies());
        addCoordinates(coordinates, config.providedDependencies().keySet());
        addCoordinates(coordinates, config.managedProvidedDependencies());
        addCoordinates(coordinates, config.devDependencies().keySet());
        addCoordinates(coordinates, config.managedDevDependencies());
        addCoordinates(coordinates, config.testDependencies().keySet());
        addCoordinates(coordinates, config.managedTestDependencies());
        addCoordinates(coordinates, config.annotationProcessors().keySet());
        addCoordinates(coordinates, config.managedAnnotationProcessors());
        addCoordinates(coordinates, config.testAnnotationProcessors().keySet());
        addCoordinates(coordinates, config.managedTestAnnotationProcessors());
        return coordinates;
    }

    private static void addCoordinates(List<String> coordinates, Iterable<String> values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                coordinates.add(value);
            }
        }
    }

    private record BoundarySignal(String coordinatePrefix, String area, String nextStep) {
        private boolean matches(String coordinate) {
            return coordinate.equals(coordinatePrefix) || coordinate.startsWith(coordinatePrefix + ":");
        }
    }
}
