package com.zolt.build;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

final class SpringBootAotNativeInputs {
    private final Path projectRoot;
    private final String outputRoot;
    private final SpringBootAotOutputEvidenceService evidenceService;

    SpringBootAotNativeInputs(Path projectRoot) {
        this(projectRoot, "target");
    }

    SpringBootAotNativeInputs(Path projectRoot, String outputRoot) {
        this(projectRoot, outputRoot, new SpringBootAotOutputEvidenceService());
    }

    SpringBootAotNativeInputs(
            Path projectRoot,
            String outputRoot,
            SpringBootAotOutputEvidenceService evidenceService) {
        this.projectRoot = projectRoot.toAbsolutePath().normalize();
        this.outputRoot = outputRoot == null || outputRoot.isBlank() ? "target" : outputRoot;
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
        requireFiles(evidence.reachabilityMetadata(), "Spring Boot AOT reachability metadata", evidence.nativeMetadataDirectory());
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
}
