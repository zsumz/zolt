package sh.zolt.toml.generated;

import sh.zolt.project.GeneratedSourceKind;
import sh.zolt.project.GeneratedSourceStep;
import sh.zolt.project.OpenApiGenerationSettings;
import sh.zolt.project.VersionPolicy;
import sh.zolt.toml.ZoltConfigException;
import sh.zolt.toml.support.TomlScalars;
import sh.zolt.toml.support.TomlValidation;
import sh.zolt.toml.support.TomlVersions;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.tomlj.TomlTable;

final class OpenApiGeneratedSourceParser {
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

    private OpenApiGeneratedSourceParser() {
    }

    static Tool parseTool(
            TomlTable table,
            Map<String, String> versionAliases) {
        if (table == null) {
            return new Tool(Optional.empty(), Optional.empty(), Optional.empty());
        }
        TomlValidation.validateKeysWithVersionRefHint("generated.openapiTool", table, GENERATED_OPENAPI_TOOL_KEYS);
        return new Tool(
                TomlScalars.optionalString(table, "generated.openapiTool", "coordinate"),
                TomlVersions.optionalVersionOrRef(
                        table,
                        "generated.openapiTool",
                        VersionPolicy.Context.TOOL_DEPENDENCY,
                        versionAliases),
                TomlVersions.optionalVersionRef(table, "generated.openapiTool", versionAliases));
    }

    static Map<String, OpenApiGenerationSettings> parsePresets(TomlTable table) {
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
            presets.put(id, parseSettings(presetTable, section));
        }
        return java.util.Collections.unmodifiableMap(new java.util.TreeMap<>(presets));
    }

    static GeneratedSourceStep parseStep(
            String id,
            TomlTable stepTable,
            String stepSection,
            Tool tool,
            Map<String, OpenApiGenerationSettings> presets) {
        TomlValidation.validateKeysWithVersionRefHint(stepSection, stepTable, GENERATED_OPENAPI_SOURCE_KEYS);
        Optional<String> presetId = TomlScalars.optionalString(stepTable, stepSection, "preset");
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
        OpenApiGenerationSettings stepSettings = parseSettings(stepTable, stepSection);
        OpenApiGenerationSettings merged = mergeSettings(tool, presetId, preset, stepSettings);
        return new GeneratedSourceStep(
                id,
                GeneratedSourceKind.OPENAPI,
                "java",
                TomlScalars.requiredString(stepTable, stepSection, "output"),
                List.of(TomlScalars.requiredString(stepTable, stepSection, "input")),
                TomlScalars.booleanOrDefault(stepTable, stepSection, "required", true),
                TomlScalars.booleanOrDefault(stepTable, stepSection, "clean", true),
                merged);
    }

    private static OpenApiGenerationSettings parseSettings(TomlTable table, String section) {
        Map<String, String> options = TomlScalars.stringMap(optionalTable(table, "options"), section + ".options");
        Map<String, String> additionalProperties =
                TomlScalars.stringMap(optionalTable(table, "additionalProperties"), section + ".additionalProperties");
        Map<String, String> configOptions =
                TomlScalars.stringMap(optionalTable(table, "configOptions"), section + ".configOptions");
        Map<String, String> globalProperties =
                TomlScalars.stringMap(optionalTable(table, "globalProperties"), section + ".globalProperties");
        Map<String, String> typeMappings =
                TomlScalars.stringMap(optionalTable(table, "typeMappings"), section + ".typeMappings");
        Map<String, String> importMappings =
                TomlScalars.stringMap(optionalTable(table, "importMappings"), section + ".importMappings");
        validateOptionKeys(section + ".options", options);
        validateOptionKeys(section + ".additionalProperties", additionalProperties);
        validateOptionKeys(section + ".configOptions", configOptions);
        validateOptionKeys(section + ".globalProperties", globalProperties);
        return new OpenApiGenerationSettings(
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                TomlScalars.optionalString(table, section, "preset"),
                TomlScalars.optionalString(table, section, "generator"),
                TomlScalars.optionalString(table, section, "library"),
                TomlScalars.optionalString(table, section, "apiPackage"),
                TomlScalars.optionalString(table, section, "modelPackage"),
                TomlScalars.optionalString(table, section, "invokerPackage"),
                TomlScalars.optionalString(table, section, "config"),
                TomlScalars.optionalString(table, section, "templateDir"),
                TomlScalars.optionalBoolean(table, section, "validateSpec"),
                options,
                additionalProperties,
                configOptions,
                globalProperties,
                typeMappings,
                importMappings);
    }

    private static void validateOptionKeys(String section, Map<String, String> values) {
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

    private static OpenApiGenerationSettings mergeSettings(
            Tool tool,
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

    private static TomlTable optionalTable(TomlTable table, String key) {
        return table.getTable(List.of(key));
    }

    record Tool(
            Optional<String> coordinate,
            Optional<String> version,
            Optional<String> versionRef) {
    }
}
