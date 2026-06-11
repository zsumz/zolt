package com.zolt.project;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

public record OpenApiGenerationSettings(
        Optional<String> toolCoordinate,
        Optional<String> toolVersion,
        Optional<String> preset,
        Optional<String> generator,
        Optional<String> library,
        Optional<String> apiPackage,
        Optional<String> modelPackage,
        Optional<String> invokerPackage,
        Optional<String> config,
        Optional<String> templateDir,
        Map<String, String> options,
        Map<String, String> globalProperties,
        Map<String, String> typeMappings,
        Map<String, String> importMappings) {
    public OpenApiGenerationSettings {
        toolCoordinate = toolCoordinate == null ? Optional.empty() : toolCoordinate;
        toolVersion = toolVersion == null ? Optional.empty() : toolVersion;
        preset = preset == null ? Optional.empty() : preset;
        generator = generator == null ? Optional.empty() : generator;
        library = library == null ? Optional.empty() : library;
        apiPackage = apiPackage == null ? Optional.empty() : apiPackage;
        modelPackage = modelPackage == null ? Optional.empty() : modelPackage;
        invokerPackage = invokerPackage == null ? Optional.empty() : invokerPackage;
        config = config == null ? Optional.empty() : config;
        templateDir = templateDir == null ? Optional.empty() : templateDir;
        options = sortedCopy(options);
        globalProperties = sortedCopy(globalProperties);
        typeMappings = sortedCopy(typeMappings);
        importMappings = sortedCopy(importMappings);
    }

    public static OpenApiGenerationSettings empty() {
        return new OpenApiGenerationSettings(
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of());
    }

    private static Map<String, String> sortedCopy(Map<String, String> values) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        return Collections.unmodifiableMap(new TreeMap<>(values));
    }
}
