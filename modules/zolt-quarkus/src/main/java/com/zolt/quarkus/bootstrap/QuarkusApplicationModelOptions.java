package com.zolt.quarkus.bootstrap;

import com.zolt.quarkus.QuarkusAugmentationException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public record QuarkusApplicationModelOptions(
        List<QuarkusArtifactKey> parentFirstArtifacts,
        List<QuarkusArtifactKey> runnerParentFirstArtifacts,
        Map<QuarkusArtifactKey, List<String>> removedResources) {
    private static final QuarkusArtifactKey QUARKUS_BUILDER =
            new QuarkusArtifactKey("io.quarkus", "quarkus-builder", Optional.empty(), Optional.of("jar"));
    private static final QuarkusArtifactKey MICROPROFILE_CONFIG_API =
            new QuarkusArtifactKey(
                    "org.eclipse.microprofile.config",
                    "microprofile-config-api",
                    Optional.empty(),
                    Optional.of("jar"));
    private static final QuarkusArtifactKey SMALLRYE_CONFIG_COMMON =
            new QuarkusArtifactKey("io.smallrye.config", "smallrye-config-common", Optional.empty(), Optional.of("jar"));
    private static final QuarkusArtifactKey SMALLRYE_CONFIG_CORE =
            new QuarkusArtifactKey("io.smallrye.config", "smallrye-config-core", Optional.empty(), Optional.of("jar"));
    private static final QuarkusArtifactKey QUARKUS_REST =
            new QuarkusArtifactKey("io.quarkus", "quarkus-rest", Optional.empty(), Optional.of("jar"));
    private static final QuarkusArtifactKey QUARKUS_ARC =
            new QuarkusArtifactKey("io.quarkus", "quarkus-arc", Optional.empty(), Optional.of("jar"));
    private static final String TEST_HTTP_ENDPOINT_PROVIDER_SERVICE =
            "META-INF/services/io.quarkus.runtime.test.TestHttpEndpointProvider";
    private static final String TEST_SCOPE_SETUP_SERVICE =
            "META-INF/services/io.quarkus.runtime.test.TestScopeSetup";
    public static final QuarkusApplicationModelOptions DEFAULT =
            new QuarkusApplicationModelOptions(List.of(), List.of(), Map.of());
    public static final QuarkusApplicationModelOptions TEST_BOOTSTRAP =
            new QuarkusApplicationModelOptions(
                    List.of(
                            QUARKUS_BUILDER,
                            MICROPROFILE_CONFIG_API,
                            SMALLRYE_CONFIG_COMMON,
                            SMALLRYE_CONFIG_CORE),
                    List.of(),
                    Map.of(
                            QUARKUS_REST,
                            List.of(TEST_HTTP_ENDPOINT_PROVIDER_SERVICE),
                            QUARKUS_ARC,
                            List.of(TEST_SCOPE_SETUP_SERVICE)));

    public QuarkusApplicationModelOptions {
        if (parentFirstArtifacts == null) {
            throw new QuarkusAugmentationException("Quarkus application model options require parent-first artifacts.");
        }
        if (runnerParentFirstArtifacts == null) {
            throw new QuarkusAugmentationException(
                    "Quarkus application model options require runner-parent-first artifacts.");
        }
        if (removedResources == null) {
            throw new QuarkusAugmentationException("Quarkus application model options require removed resources.");
        }
        parentFirstArtifacts = List.copyOf(parentFirstArtifacts);
        runnerParentFirstArtifacts = List.copyOf(runnerParentFirstArtifacts);
        removedResources = copyRemovedResources(removedResources);
    }

    private static Map<QuarkusArtifactKey, List<String>> copyRemovedResources(
            Map<QuarkusArtifactKey, List<String>> removedResources) {
        java.util.LinkedHashMap<QuarkusArtifactKey, List<String>> copy = new java.util.LinkedHashMap<>();
        for (Map.Entry<QuarkusArtifactKey, List<String>> entry : removedResources.entrySet()) {
            if (entry.getKey() == null) {
                throw new QuarkusAugmentationException("Quarkus application model options contain a null artifact key.");
            }
            if (entry.getValue() == null) {
                throw new QuarkusAugmentationException(
                        "Quarkus application model options contain null removed resources for "
                                + entry.getKey()
                                + ".");
            }
            copy.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        return java.util.Collections.unmodifiableMap(copy);
    }
}
