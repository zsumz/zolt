package com.zolt.quarkus;

import java.util.List;
import java.util.Optional;

public record QuarkusApplicationModelOptions(
        List<QuarkusArtifactKey> parentFirstArtifacts,
        List<QuarkusArtifactKey> runnerParentFirstArtifacts) {
    private static final QuarkusArtifactKey QUARKUS_BUILDER =
            new QuarkusArtifactKey("io.quarkus", "quarkus-builder", Optional.empty(), Optional.of("jar"));
    private static final QuarkusArtifactKey MICROPROFILE_CONFIG_API =
            new QuarkusArtifactKey(
                    "org.eclipse.microprofile.config",
                    "microprofile-config-api",
                    Optional.empty(),
                    Optional.of("jar"));
    private static final QuarkusArtifactKey SMALLRYE_CONFIG =
            new QuarkusArtifactKey("io.smallrye.config", "smallrye-config", Optional.empty(), Optional.of("jar"));
    private static final QuarkusArtifactKey SMALLRYE_CONFIG_COMMON =
            new QuarkusArtifactKey("io.smallrye.config", "smallrye-config-common", Optional.empty(), Optional.of("jar"));
    private static final QuarkusArtifactKey SMALLRYE_CONFIG_CORE =
            new QuarkusArtifactKey("io.smallrye.config", "smallrye-config-core", Optional.empty(), Optional.of("jar"));
    public static final QuarkusApplicationModelOptions DEFAULT =
            new QuarkusApplicationModelOptions(List.of(), List.of());
    public static final QuarkusApplicationModelOptions TEST_BOOTSTRAP =
            new QuarkusApplicationModelOptions(
                    List.of(
                            QUARKUS_BUILDER,
                            MICROPROFILE_CONFIG_API,
                            SMALLRYE_CONFIG,
                            SMALLRYE_CONFIG_COMMON,
                            SMALLRYE_CONFIG_CORE),
                    List.of());

    public QuarkusApplicationModelOptions {
        if (parentFirstArtifacts == null) {
            throw new QuarkusAugmentationException("Quarkus application model options require parent-first artifacts.");
        }
        if (runnerParentFirstArtifacts == null) {
            throw new QuarkusAugmentationException(
                    "Quarkus application model options require runner-parent-first artifacts.");
        }
        parentFirstArtifacts = List.copyOf(parentFirstArtifacts);
        runnerParentFirstArtifacts = List.copyOf(runnerParentFirstArtifacts);
    }
}
