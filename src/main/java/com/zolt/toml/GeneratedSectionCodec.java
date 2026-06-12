package com.zolt.toml;

import com.zolt.project.BuildSettings;
import com.zolt.project.GeneratedSourceKind;
import com.zolt.project.GeneratedSourceStep;
import com.zolt.project.OpenApiGenerationSettings;
import com.zolt.project.VersionPolicy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import org.tomlj.TomlArray;
import org.tomlj.TomlTable;

final class GeneratedSectionCodec {
    private static final Set<String> GENERATED_KEYS = Set.of("main", "test", "openapiTool", "openapiPresets");
    private static final Set<String> GENERATED_DECLARED_SOURCE_KEYS =
            Set.of("kind", "language", "output", "inputs", "required", "clean");
    private static final Set<String> GENERATED_OPENAPI_SOURCE_KEYS = Set.of(
            "kind",
            "language",
            "input",
            "output",
            "required",
            "clean",
            "preset",
            "generator",
            "library",
            "apiPackage",
            "modelPackage",
            "invokerPackage",
            "config",
            "templateDir",
            "validateSpec",
            "options",
            "additionalProperties",
            "configOptions",
            "globalProperties",
            "typeMappings",
            "importMappings");
    private static final Set<String> GENERATED_OPENAPI_PRESET_KEYS = Set.of(
            "generator",
            "library",
            "apiPackage",
            "modelPackage",
            "invokerPackage",
            "config",
            "templateDir",
            "validateSpec",
            "options",
            "additionalProperties",
            "configOptions",
            "globalProperties",
            "typeMappings",
            "importMappings");
    private static final Set<String> GENERATED_OPENAPI_TOOL_KEYS = Set.of("coordinate", "version", "versionRef");
    private static final Set<String> GENERATED_OPENAPI_POST_PROCESS_KEYS = Set.of(
            "enablepostprocessfile",
            "postprocessfile",
            "apifilepostprocessfile",
            "modelfilepostprocessfile");

    private GeneratedSectionCodec() {
    }

    static BuildSettings parse(
            TomlTable table,
            BuildSettings build,
            Map<String, String> versionAliases) {
        if (table == null) {
            return build;
        }
        TomlValidation.validateKeysWithVersionRefHint("generated", table, GENERATED_KEYS);
        OpenApiTool tool = parseOpenApiTool(optionalTable(table, "openapiTool"), versionAliases);
        Map<String, OpenApiGenerationSettings> presets = parseOpenApiPresets(optionalTable(table, "openapiPresets"));
        return build.withGeneratedSources(
                parseGeneratedSourceScope(optionalTable(table, "main"), "generated.main", tool, presets),
                parseGeneratedSourceScope(optionalTable(table, "test"), "generated.test", tool, presets));
    }

    static void write(StringBuilder toml, BuildSettings build) {
        writeOpenApiTool(toml, build);
        writeGeneratedSourceScope(toml, "generated.main", build.generatedMainSources());
        writeGeneratedSourceScope(toml, "generated.test", build.generatedTestSources());
    }

    private static List<GeneratedSourceStep> parseGeneratedSourceScope(
            TomlTable table,
            String section,
            OpenApiTool tool,
            Map<String, OpenApiGenerationSettings> presets) {
        if (table == null) {
            return List.of();
        }
        List<GeneratedSourceStep> steps = new ArrayList<>();
        for (String id : table.keySet()) {
            TomlTable stepTable = optionalTable(table, id);
            if (stepTable == null) {
                throw new ZoltConfigException(
                        "Invalid value for [" + section + "]." + id + " in zolt.toml. Use a table with kind, language, output, and inputs.");
            }
            String stepSection = section + "." + id;
            String kindValue = requiredString(stepTable, stepSection, "kind");
            GeneratedSourceKind kind = GeneratedSourceKind.fromConfigValue(kindValue)
                    .orElseThrow(() -> new ZoltConfigException(
                            "Unsupported generated source kind `"
                                    + kindValue
                                    + "` in zolt.toml. Supported generated source kinds are: "
                                    + GeneratedSourceKind.supportedValues()
                                    + "."));
            String language = requiredString(stepTable, stepSection, "language");
            if (!"java".equals(language)) {
                throw new ZoltConfigException(
                        "Unsupported generated source language `"
                                + language
                                + "` in zolt.toml. Supported generated source languages are: java.");
            }
            if (kind == GeneratedSourceKind.OPENAPI) {
                TomlValidation.validateKeysWithVersionRefHint(stepSection, stepTable, GENERATED_OPENAPI_SOURCE_KEYS);
                steps.add(parseOpenApiGeneratedSourceStep(id, stepTable, stepSection, tool, presets));
            } else {
                TomlValidation.validateKeysWithVersionRefHint(stepSection, stepTable, GENERATED_DECLARED_SOURCE_KEYS);
                List<String> inputs = stringListOrDefault(stepTable, stepSection, "inputs", List.of());
                if (inputs.isEmpty()) {
                    throw new ZoltConfigException(
                            "Missing required field ["
                                    + stepSection
                                    + "].inputs in zolt.toml. Add at least one project-relative input path.");
                }
                steps.add(new GeneratedSourceStep(
                        id,
                        kind,
                        language,
                        requiredString(stepTable, stepSection, "output"),
                        inputs,
                        booleanOrDefault(stepTable, stepSection, "required", true),
                        booleanOrDefault(stepTable, stepSection, "clean", false)));
            }
        }
        return List.copyOf(steps);
    }

    private static GeneratedSourceStep parseOpenApiGeneratedSourceStep(
            String id,
            TomlTable stepTable,
            String stepSection,
            OpenApiTool tool,
            Map<String, OpenApiGenerationSettings> presets) {
        Optional<String> presetId = optionalString(stepTable, stepSection, "preset");
        OpenApiGenerationSettings preset = OpenApiGenerationSettings.empty();
        if (presetId.isPresent()) {
            preset = presets.get(presetId.orElseThrow());
            if (preset == null) {
                throw new ZoltConfigException(
                        "Unknown OpenAPI preset `"
                                + presetId.orElseThrow()
                                + "` in ["
                                + stepSection
                                + "]. Add [generated.openapiPresets."
                                + presetId.orElseThrow()
                                + "] or remove the preset reference.");
            }
        }
        OpenApiGenerationSettings stepSettings = parseOpenApiGenerationSettings(stepTable, stepSection);
        OpenApiGenerationSettings merged = mergeOpenApiSettings(tool, presetId, preset, stepSettings);
        return new GeneratedSourceStep(
                id,
                GeneratedSourceKind.OPENAPI,
                "java",
                requiredString(stepTable, stepSection, "output"),
                List.of(requiredString(stepTable, stepSection, "input")),
                booleanOrDefault(stepTable, stepSection, "required", true),
                booleanOrDefault(stepTable, stepSection, "clean", true),
                merged);
    }

    private static OpenApiTool parseOpenApiTool(
            TomlTable table,
            Map<String, String> versionAliases) {
        if (table == null) {
            return new OpenApiTool(Optional.empty(), Optional.empty(), Optional.empty());
        }
        TomlValidation.validateKeysWithVersionRefHint("generated.openapiTool", table, GENERATED_OPENAPI_TOOL_KEYS);
        return new OpenApiTool(
                optionalString(table, "generated.openapiTool", "coordinate"),
                TomlVersions.optionalVersionOrRef(
                        table,
                        "generated.openapiTool",
                        VersionPolicy.Context.TOOL_DEPENDENCY,
                        versionAliases),
                TomlVersions.optionalVersionRef(table, "generated.openapiTool", versionAliases));
    }

    private static Map<String, OpenApiGenerationSettings> parseOpenApiPresets(TomlTable table) {
        if (table == null) {
            return Map.of();
        }
        Map<String, OpenApiGenerationSettings> presets = new LinkedHashMap<>();
        for (String id : table.keySet()) {
            TomlTable presetTable = optionalTable(table, id);
            if (presetTable == null) {
                throw new ZoltConfigException(
                        "Invalid value for [generated.openapiPresets]."
                                + id
                                + " in zolt.toml. Use a table with OpenAPI generator settings.");
            }
            String section = "generated.openapiPresets." + id;
            TomlValidation.validateKeysWithVersionRefHint(section, presetTable, GENERATED_OPENAPI_PRESET_KEYS);
            presets.put(id, parseOpenApiGenerationSettings(presetTable, section));
        }
        return java.util.Collections.unmodifiableMap(new java.util.TreeMap<>(presets));
    }

    private static OpenApiGenerationSettings parseOpenApiGenerationSettings(TomlTable table, String section) {
        Map<String, String> options = stringMap(optionalTable(table, "options"), section + ".options");
        Map<String, String> additionalProperties =
                stringMap(optionalTable(table, "additionalProperties"), section + ".additionalProperties");
        Map<String, String> configOptions = stringMap(optionalTable(table, "configOptions"), section + ".configOptions");
        Map<String, String> globalProperties =
                stringMap(optionalTable(table, "globalProperties"), section + ".globalProperties");
        Map<String, String> typeMappings = stringMap(optionalTable(table, "typeMappings"), section + ".typeMappings");
        Map<String, String> importMappings =
                stringMap(optionalTable(table, "importMappings"), section + ".importMappings");
        validateOpenApiOptionKeys(section + ".options", options);
        validateOpenApiOptionKeys(section + ".additionalProperties", additionalProperties);
        validateOpenApiOptionKeys(section + ".configOptions", configOptions);
        validateOpenApiOptionKeys(section + ".globalProperties", globalProperties);
        return new OpenApiGenerationSettings(
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                optionalString(table, section, "preset"),
                optionalString(table, section, "generator"),
                optionalString(table, section, "library"),
                optionalString(table, section, "apiPackage"),
                optionalString(table, section, "modelPackage"),
                optionalString(table, section, "invokerPackage"),
                optionalString(table, section, "config"),
                optionalString(table, section, "templateDir"),
                optionalBoolean(table, section, "validateSpec"),
                options,
                additionalProperties,
                configOptions,
                globalProperties,
                typeMappings,
                importMappings);
    }

    private static void validateOpenApiOptionKeys(String section, Map<String, String> values) {
        for (String key : values.keySet()) {
            String normalized = key.toLowerCase(Locale.ROOT);
            if (GENERATED_OPENAPI_POST_PROCESS_KEYS.contains(normalized) || normalized.contains("postprocess")) {
                throw new ZoltConfigException(
                        "Unsupported OpenAPI generator option ["
                                + section
                                + "]."
                                + key
                                + " in zolt.toml. Zolt does not run generator post-processing hooks; remove the option or model the behavior as a Zolt-owned generated-source feature.");
            }
        }
    }

    private static OpenApiGenerationSettings mergeOpenApiSettings(
            OpenApiTool tool,
            Optional<String> presetId,
            OpenApiGenerationSettings preset,
            OpenApiGenerationSettings step) {
        return new OpenApiGenerationSettings(
                tool.coordinate(),
                tool.version(),
                tool.versionRef(),
                presetId,
                step.generator().or(() -> preset.generator()),
                step.library().or(() -> preset.library()),
                step.apiPackage().or(() -> preset.apiPackage()),
                step.modelPackage().or(() -> preset.modelPackage()),
                step.invokerPackage().or(() -> preset.invokerPackage()),
                step.config().or(() -> preset.config()),
                step.templateDir().or(() -> preset.templateDir()),
                step.validateSpec().or(() -> preset.validateSpec()),
                mergedMap(preset.options(), step.options()),
                mergedMap(preset.additionalProperties(), step.additionalProperties()),
                mergedMap(preset.configOptions(), step.configOptions()),
                mergedMap(preset.globalProperties(), step.globalProperties()),
                mergedMap(preset.typeMappings(), step.typeMappings()),
                mergedMap(preset.importMappings(), step.importMappings()));
    }

    private static Map<String, String> mergedMap(Map<String, String> preset, Map<String, String> step) {
        Map<String, String> merged = new java.util.TreeMap<>();
        merged.putAll(preset);
        merged.putAll(step);
        return merged;
    }

    private static void writeOpenApiTool(StringBuilder toml, BuildSettings build) {
        Optional<OpenApiGenerationSettings> settings = java.util.stream.Stream
                .concat(build.generatedMainSources().stream(), build.generatedTestSources().stream())
                .filter(step -> step.kind() == GeneratedSourceKind.OPENAPI)
                .map(GeneratedSourceStep::openApi)
                .filter(openApi -> openApi.toolCoordinate().isPresent()
                        || openApi.toolVersion().isPresent()
                        || openApi.toolVersionRef().isPresent())
                .findFirst();
        if (settings.isEmpty()) {
            return;
        }
        toml.append("\n[generated.openapiTool]\n");
        settings.orElseThrow().toolCoordinate().ifPresent(coordinate -> writeAssignment(toml, "coordinate", coordinate));
        if (settings.orElseThrow().toolVersionRef().isPresent()) {
            writeAssignment(toml, "versionRef", settings.orElseThrow().toolVersionRef().orElseThrow());
        } else {
            settings.orElseThrow().toolVersion().ifPresent(version -> writeAssignment(toml, "version", version));
        }
    }

    private static void writeGeneratedSourceScope(
            StringBuilder toml,
            String section,
            List<GeneratedSourceStep> steps) {
        if (steps.isEmpty()) {
            return;
        }
        for (GeneratedSourceStep step : steps.stream()
                .sorted(java.util.Comparator.comparing(GeneratedSourceStep::id))
                .toList()) {
            toml.append("\n[").append(section).append('.').append(step.id()).append("]\n");
            writeAssignment(toml, "kind", step.kind().configValue());
            writeAssignment(toml, "language", step.language());
            writeAssignment(toml, "output", step.output());
            if (step.kind() == GeneratedSourceKind.OPENAPI) {
                writeAssignment(toml, "input", step.inputs().getFirst());
                writeOpenApiSettings(toml, step.openApi());
            } else {
                writeStringArray(toml, "inputs", step.inputs());
            }
            if (!step.required()) {
                writeAssignment(toml, "required", false);
            }
            if (step.kind() == GeneratedSourceKind.OPENAPI && !step.clean()) {
                writeAssignment(toml, "clean", false);
            } else if (step.kind() != GeneratedSourceKind.OPENAPI && step.clean()) {
                writeAssignment(toml, "clean", true);
            }
        }
    }

    private static void writeOpenApiSettings(StringBuilder toml, OpenApiGenerationSettings settings) {
        settings.generator().ifPresent(value -> writeAssignment(toml, "generator", value));
        settings.library().ifPresent(value -> writeAssignment(toml, "library", value));
        settings.apiPackage().ifPresent(value -> writeAssignment(toml, "apiPackage", value));
        settings.modelPackage().ifPresent(value -> writeAssignment(toml, "modelPackage", value));
        settings.invokerPackage().ifPresent(value -> writeAssignment(toml, "invokerPackage", value));
        settings.config().ifPresent(value -> writeAssignment(toml, "config", value));
        settings.templateDir().ifPresent(value -> writeAssignment(toml, "templateDir", value));
        settings.validateSpec().ifPresent(value -> writeAssignment(toml, "validateSpec", value));
        if (!settings.options().isEmpty()) {
            writeInlineStringMap(toml, "options", settings.options());
        }
        if (!settings.additionalProperties().isEmpty()) {
            writeInlineStringMap(toml, "additionalProperties", settings.additionalProperties());
        }
        if (!settings.configOptions().isEmpty()) {
            writeInlineStringMap(toml, "configOptions", settings.configOptions());
        }
        if (!settings.globalProperties().isEmpty()) {
            writeInlineStringMap(toml, "globalProperties", settings.globalProperties());
        }
        if (!settings.typeMappings().isEmpty()) {
            writeInlineStringMap(toml, "typeMappings", settings.typeMappings());
        }
        if (!settings.importMappings().isEmpty()) {
            writeInlineStringMap(toml, "importMappings", settings.importMappings());
        }
    }

    private static TomlTable optionalTable(TomlTable table, String key) {
        return table.getTable(List.of(key));
    }

    private static String requiredString(TomlTable table, String section, String key) {
        String value = table.getString(key);
        if (value == null || value.isBlank()) {
            throw new ZoltConfigException(
                    "Missing required field [" + section + "]." + key + " in zolt.toml. Add a non-empty string value.");
        }
        return value;
    }

    private static Optional<String> optionalString(TomlTable table, String section, String key) {
        String value = table.getString(key);
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(value);
    }

    private static boolean booleanOrDefault(TomlTable table, String section, String key, boolean defaultValue) {
        Object rawValue = table.get(List.of(key));
        if (rawValue == null) {
            return defaultValue;
        }
        if (!(rawValue instanceof Boolean value)) {
            throw new ZoltConfigException(
                    "Invalid value for [" + section + "]." + key + " in zolt.toml. Use true or false.");
        }
        return value;
    }

    private static Optional<Boolean> optionalBoolean(TomlTable table, String section, String key) {
        Object rawValue = table.get(List.of(key));
        if (rawValue == null) {
            return Optional.empty();
        }
        if (!(rawValue instanceof Boolean value)) {
            throw new ZoltConfigException(
                    "Invalid value for [" + section + "]." + key + " in zolt.toml. Use true or false.");
        }
        return Optional.of(value);
    }

    private static Map<String, String> stringMap(TomlTable table, String section) {
        if (table == null) {
            return Map.of();
        }

        Map<String, String> values = new LinkedHashMap<>();
        for (String key : table.keySet()) {
            Object rawValue = table.get(List.of(key));
            if (!(rawValue instanceof String value) || value.isBlank()) {
                throw new ZoltConfigException(
                        "Invalid value for [" + section + "]." + key + " in zolt.toml. Use a non-empty string value.");
            }
            values.put(key, value);
        }
        return values;
    }

    private static List<String> stringListOrDefault(
            TomlTable table,
            String section,
            String key,
            List<String> defaultValue) {
        Object rawValue = table.get(List.of(key));
        if (rawValue == null) {
            return defaultValue;
        }
        if (!(rawValue instanceof TomlArray array)) {
            throw new ZoltConfigException(
                    "Invalid value for [" + section + "]." + key + " in zolt.toml. Use an array of strings.");
        }
        ArrayList<String> values = new ArrayList<>();
        for (int index = 0; index < array.size(); index++) {
            String value = array.getString(index);
            if (value == null || value.isBlank()) {
                throw new ZoltConfigException(
                        "Invalid value for [" + section + "]." + key + "[" + index + "] in zolt.toml. Use a non-empty string.");
            }
            values.add(value);
        }
        return List.copyOf(values);
    }

    private static void writeAssignment(StringBuilder toml, String key, boolean value) {
        toml.append(key).append(" = ").append(value).append('\n');
    }

    private static void writeAssignment(StringBuilder toml, String key, String value) {
        toml.append(key).append(" = ").append(quote(value)).append('\n');
    }

    private static void writeStringArray(StringBuilder toml, String key, List<String> values) {
        toml.append(key).append(" = [");
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) {
                toml.append(", ");
            }
            toml.append(quote(values.get(index)));
        }
        toml.append("]\n");
    }

    private static void writeInlineStringMap(StringBuilder toml, String key, Map<String, String> values) {
        toml.append(key).append(" = { ");
        int index = 0;
        for (Map.Entry<String, String> entry : new TreeMap<>(values).entrySet()) {
            if (index > 0) {
                toml.append(", ");
            }
            toml.append(quote(entry.getKey())).append(" = ").append(quote(entry.getValue()));
            index++;
        }
        toml.append(" }\n");
    }

    private static String quote(String value) {
        return "\"" + value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t")
                + "\"";
    }

    private record OpenApiTool(
            Optional<String> coordinate,
            Optional<String> version,
            Optional<String> versionRef) {
    }
}
