package sh.zolt.project;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

public record OpenApiGenerationSettings(
        Optional<String> toolCoordinate,
        Optional<String> toolVersion,
        Optional<String> toolVersionRef,
        Optional<String> preset,
        Optional<String> generator,
        Optional<String> library,
        Optional<String> apiPackage,
        Optional<String> modelPackage,
        Optional<String> invokerPackage,
        Optional<String> config,
        Optional<String> templateDir,
        Optional<Boolean> validateSpec,
        Map<String, String> options,
        Map<String, String> additionalProperties,
        Map<String, String> configOptions,
        Map<String, String> globalProperties,
        Map<String, String> typeMappings,
        Map<String, String> importMappings) {
    public OpenApiGenerationSettings {
        toolCoordinate = toolCoordinate == null ? Optional.empty() : toolCoordinate;
        toolVersion = toolVersion == null ? Optional.empty() : toolVersion;
        toolVersionRef = toolVersionRef == null ? Optional.empty() : toolVersionRef;
        preset = preset == null ? Optional.empty() : preset;
        generator = generator == null ? Optional.empty() : generator;
        library = library == null ? Optional.empty() : library;
        apiPackage = apiPackage == null ? Optional.empty() : apiPackage;
        modelPackage = modelPackage == null ? Optional.empty() : modelPackage;
        invokerPackage = invokerPackage == null ? Optional.empty() : invokerPackage;
        config = config == null ? Optional.empty() : config;
        templateDir = templateDir == null ? Optional.empty() : templateDir;
        validateSpec = validateSpec == null ? Optional.empty() : validateSpec;
        options = sortedCopy(options);
        additionalProperties = sortedCopy(additionalProperties);
        configOptions = sortedCopy(configOptions);
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
                Optional.empty(),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of());
    }

    public OpenApiGenerationSettings(
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
            Optional<Boolean> validateSpec,
            Map<String, String> options,
            Map<String, String> additionalProperties,
            Map<String, String> configOptions,
            Map<String, String> globalProperties,
            Map<String, String> typeMappings,
            Map<String, String> importMappings) {
        this(
                toolCoordinate,
                toolVersion,
                Optional.empty(),
                preset,
                generator,
                library,
                apiPackage,
                modelPackage,
                invokerPackage,
                config,
                templateDir,
                validateSpec,
                options,
                additionalProperties,
                configOptions,
                globalProperties,
                typeMappings,
                importMappings);
    }

    private static Map<String, String> sortedCopy(Map<String, String> values) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        return Collections.unmodifiableMap(new TreeMap<>(values));
    }
}
