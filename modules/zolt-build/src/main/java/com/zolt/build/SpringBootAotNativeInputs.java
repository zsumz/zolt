package com.zolt.build;

import com.zolt.project.ProjectConfig;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

final class SpringBootAotNativeInputs {
    private final Path projectRoot;
    private final String outputRoot;
    private final List<Path> freshnessInputs;
    private final SpringBootAotOutputEvidenceService evidenceService;

    SpringBootAotNativeInputs(Path projectRoot) {
        this(projectRoot, "target");
    }

    SpringBootAotNativeInputs(Path projectRoot, String outputRoot) {
        this(projectRoot, outputRoot, List.of(), new SpringBootAotOutputEvidenceService());
    }

    SpringBootAotNativeInputs(Path projectRoot, String outputRoot, List<Path> freshnessInputs) {
        this(projectRoot, outputRoot, freshnessInputs, new SpringBootAotOutputEvidenceService());
    }

    SpringBootAotNativeInputs(
            Path projectRoot,
            String outputRoot,
            List<Path> freshnessInputs,
            SpringBootAotOutputEvidenceService evidenceService) {
        this.projectRoot = projectRoot.toAbsolutePath().normalize();
        this.outputRoot = outputRoot == null || outputRoot.isBlank() ? "target" : outputRoot;
        this.freshnessInputs = freshnessInputs == null ? List.of() : List.copyOf(freshnessInputs);
        this.evidenceService = evidenceService;
    }

    List<Path> classpathEntries() {
        SpringBootAotOutputEvidence evidence = evidenceService.collect(projectRoot, outputRoot);
        requireDirectory(evidence.sourcesDirectory(), "Spring Boot AOT sources");
        requireFiles(evidence.generatedSources(), "Spring Boot AOT generated source files", evidence.sourcesDirectory());
        requireDirectory(evidence.classesDirectory(), "compiled Spring Boot AOT classes");
        requireFiles(evidence.generatedClasses(), "compiled Spring Boot AOT class files", evidence.classesDirectory());
        requireDirectory(evidence.resourcesDirectory(), "Spring Boot AOT resources");
        requireDirectory(evidence.nativeMetadataDirectory(), "Spring Boot AOT native metadata");
        requireFiles(evidence.reflectionMetadata(), "Spring Boot AOT reflection metadata", evidence.nativeMetadataDirectory());
        requireFresh(evidence);
        return List.of(evidence.classesDirectory(), evidence.resourcesDirectory());
    }

    private static void requireDirectory(Path path, String label) {
        if (!Files.isDirectory(path)) {
            throw new NativeImageException(
                    "Spring Boot native AOT output is missing "
                            + label
                            + " at "
                            + path
                            + ". [framework.springBoot.native] enabled = true requires Spring AOT outputs under the configured build output root before invoking Native Image.");
        }
    }

    private static void requireFiles(List<Path> paths, String label, Path directory) {
        if (paths.isEmpty()) {
            throw new NativeImageException(
                    "Spring Boot native AOT output is missing "
                            + label
                            + " under "
                            + directory
                            + ". [framework.springBoot.native] enabled = true requires complete Spring AOT output evidence before invoking Native Image.");
        }
    }

    private void requireFresh(SpringBootAotOutputEvidence evidence) {
        List<Path> aotFiles = new ArrayList<>();
        aotFiles.addAll(evidence.generatedSources());
        aotFiles.addAll(evidence.generatedClasses());
        aotFiles.addAll(evidence.generatedResources());
        aotFiles.addAll(evidence.reflectionMetadata());
        aotFiles.addAll(evidence.reachabilityMetadata());
        Optional<FileTime> oldestAot = oldestTime(aotFiles);
        Optional<FileTime> newestInput = newestTime(freshnessInputs);
        if (oldestAot.isPresent()
                && newestInput.isPresent()
                && oldestAot.orElseThrow().compareTo(newestInput.orElseThrow()) < 0) {
            throw new NativeImageException(
                    "Spring Boot native AOT output is stale under "
                            + evidence.outputRoot()
                            + ". Run `zolt build` to regenerate Spring AOT output before invoking Native Image.");
        }
    }

    private static Optional<FileTime> oldestTime(List<Path> paths) {
        return paths.stream()
                .map(SpringBootAotNativeInputs::lastModified)
                .flatMap(Optional::stream)
                .min(FileTime::compareTo);
    }

    private static Optional<FileTime> newestTime(List<Path> paths) {
        return paths.stream()
                .flatMap(path -> Files.isDirectory(path) ? files(path).stream() : Stream.of(path))
                .map(SpringBootAotNativeInputs::lastModified)
                .flatMap(Optional::stream)
                .max(FileTime::compareTo);
    }

    private static Optional<FileTime> lastModified(Path path) {
        try {
            return Files.exists(path) ? Optional.of(Files.getLastModifiedTime(path)) : Optional.empty();
        } catch (IOException exception) {
            return Optional.empty();
        }
    }

    private static List<Path> files(Path directory) {
        try (Stream<Path> paths = Files.walk(directory)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(path -> !path.getFileName().toString().startsWith(".zolt-"))
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();
        } catch (IOException exception) {
            return List.of();
        }
    }
}

final class SpringBootNativeBoundaryDiagnostics {
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

    static void rejectUnsupportedEcosystem(ProjectConfig config) {
        boundarySignal(config).ifPresent(signal -> {
            throw new NativeImageException(
                    signal.area()
                            + " are not part of Zolt's proven Spring Boot native fixture family yet. "
                            + "Zolt currently proves Spring Boot 3.3 Java 21 native rows for WebMVC, Actuator, WebMVC contract behavior, and Spring JDBC/H2 data access. "
                            + signal.nextStep());
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
