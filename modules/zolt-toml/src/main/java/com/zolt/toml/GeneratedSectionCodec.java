package com.zolt.toml;

import com.zolt.project.BuildSettings;
import com.zolt.project.GeneratedSourceKind;
import com.zolt.project.GeneratedSourceStep;
import com.zolt.project.OpenApiGenerationSettings;
import com.zolt.project.ProtobufGenerationSettings;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import org.tomlj.TomlTable;

final class GeneratedSectionCodec {
    private static final Set<String> GENERATED_KEYS = Set.of("main", "test", "openapiTool", "openapiPresets", "protobufTool");
    private static final Set<String> GENERATED_DECLARED_SOURCE_KEYS =
            Set.of("kind", "language", "output", "inputs", "required", "clean");
    private static final Set<String> GENERATED_PROTOBUF_SOURCE_KEYS =
            Set.of("kind", "language", "output", "inputs", "required", "clean", "javaPackage", "grpc", "plugin", "plugins");
    private static final Set<String> PROTOBUF_TOOL_KEYS = Set.of(
            "protocCoordinate",
            "protocVersion",
            "protocVersionRef",
            "grpcPluginCoordinate",
            "grpcPluginVersion",
            "grpcPluginVersionRef");

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
        OpenApiGeneratedSourceParser.Tool tool =
                OpenApiGeneratedSourceParser.parseTool(optionalTable(table, "openapiTool"), versionAliases);
        Map<String, OpenApiGenerationSettings> presets =
                OpenApiGeneratedSourceParser.parsePresets(optionalTable(table, "openapiPresets"));
        ProtobufGenerationSettings protobufTool = parseProtobufTool(optionalTable(table, "protobufTool"), versionAliases);
        return build.withGeneratedSources(
                parseGeneratedSourceScope(optionalTable(table, "main"), "generated.main", tool, presets, protobufTool),
                parseGeneratedSourceScope(optionalTable(table, "test"), "generated.test", tool, presets, protobufTool));
    }

    static void write(StringBuilder toml, BuildSettings build) {
        writeOpenApiTool(toml, build);
        writeGeneratedSourceScope(toml, "generated.main", build.generatedMainSources());
        writeGeneratedSourceScope(toml, "generated.test", build.generatedTestSources());
    }

    private static List<GeneratedSourceStep> parseGeneratedSourceScope(
            TomlTable table,
            String section,
            OpenApiGeneratedSourceParser.Tool tool,
            Map<String, OpenApiGenerationSettings> presets,
            ProtobufGenerationSettings protobufTool) {
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
            String kindValue = TomlScalars.requiredString(stepTable, stepSection, "kind");
            GeneratedSourceKind kind = GeneratedSourceKind.fromConfigValue(kindValue)
                    .orElseThrow(() -> new ZoltConfigException(
                            "Unsupported generated source kind `"
                                    + kindValue
                                    + "` in zolt.toml. Supported generated source kinds are: "
                                    + GeneratedSourceKind.supportedValues()
                                    + "."));
            String language = TomlScalars.requiredString(stepTable, stepSection, "language");
            if (!"java".equals(language)) {
                throw new ZoltConfigException(
                        "Unsupported generated source language `"
                                + language
                                + "` in zolt.toml. Supported generated source languages are: java.");
            }
            if (kind == GeneratedSourceKind.OPENAPI) {
                steps.add(OpenApiGeneratedSourceParser.parseStep(id, stepTable, stepSection, tool, presets));
            } else if (kind == GeneratedSourceKind.PROTOBUF) {
                steps.add(parseProtobufStep(id, stepTable, stepSection, protobufTool));
            } else {
                TomlValidation.validateKeysWithVersionRefHint(stepSection, stepTable, GENERATED_DECLARED_SOURCE_KEYS);
                List<String> inputs = TomlScalars.stringListOrDefault(stepTable, stepSection, "inputs", List.of());
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
                        TomlScalars.requiredString(stepTable, stepSection, "output"),
                        inputs,
                        TomlScalars.booleanOrDefault(stepTable, stepSection, "required", true),
                        TomlScalars.booleanOrDefault(stepTable, stepSection, "clean", false)));
            }
        }
        return List.copyOf(steps);
    }

    private static GeneratedSourceStep parseProtobufStep(
            String id,
            TomlTable table,
            String section,
            ProtobufGenerationSettings tool) {
        TomlValidation.validateKeysWithVersionRefHint(section, table, GENERATED_PROTOBUF_SOURCE_KEYS);
        if (table.contains("plugin") || table.contains("plugins")) {
            throw new ZoltConfigException(
                    "Unsupported protoc plugin configuration in ["
                            + section
                            + "]. Zolt supports only the typed Java protobuf/gRPC generator in the public beta; "
                            + "remove plugin/plugins or add a dedicated Zolt generator followUp.");
        }
        List<String> inputs = TomlScalars.stringListOrDefault(table, section, "inputs", List.of());
        if (inputs.isEmpty()) {
            throw new ZoltConfigException(
                    "Missing required field ["
                            + section
                            + "].inputs in zolt.toml. Add at least one project-relative .proto input path.");
        }
        ProtobufGenerationSettings settings = new ProtobufGenerationSettings(
                tool.protocCoordinate(),
                tool.protocVersion(),
                tool.protocVersionRef(),
                tool.grpcPluginCoordinate(),
                tool.grpcPluginVersion(),
                tool.grpcPluginVersionRef(),
                TomlScalars.optionalString(table, section, "javaPackage"),
                TomlScalars.booleanOrDefault(table, section, "grpc", true));
        return new GeneratedSourceStep(
                id,
                GeneratedSourceKind.PROTOBUF,
                TomlScalars.requiredString(table, section, "language"),
                TomlScalars.requiredString(table, section, "output"),
                inputs,
                TomlScalars.booleanOrDefault(table, section, "required", true),
                TomlScalars.booleanOrDefault(table, section, "clean", true),
                OpenApiGenerationSettings.empty(),
                settings);
    }

    private static ProtobufGenerationSettings parseProtobufTool(
            TomlTable table,
            Map<String, String> versionAliases) {
        if (table == null) {
            return ProtobufGenerationSettings.empty();
        }
        TomlValidation.validateKeysWithVersionRefHint("generated.protobufTool", table, PROTOBUF_TOOL_KEYS);
        Optional<String> protocVersionRef = TomlScalars.optionalString(table, "generated.protobufTool", "protocVersionRef");
        Optional<String> grpcPluginVersionRef = TomlScalars.optionalString(table, "generated.protobufTool", "grpcPluginVersionRef");
        Optional<String> protocVersion = versionFromAliasOrField(
                "generated.protobufTool.protocVersionRef",
                protocVersionRef,
                TomlScalars.optionalString(table, "generated.protobufTool", "protocVersion"),
                versionAliases);
        Optional<String> grpcPluginVersion = versionFromAliasOrField(
                "generated.protobufTool.grpcPluginVersionRef",
                grpcPluginVersionRef,
                TomlScalars.optionalString(table, "generated.protobufTool", "grpcPluginVersion"),
                versionAliases);
        return new ProtobufGenerationSettings(
                Optional.of(TomlScalars.stringOrDefault(
                        table,
                        "generated.protobufTool",
                        "protocCoordinate",
                        "com.google.protobuf:protoc")),
                protocVersion,
                protocVersionRef,
                Optional.of(TomlScalars.stringOrDefault(
                        table,
                        "generated.protobufTool",
                        "grpcPluginCoordinate",
                        "io.grpc:protoc-gen-grpc-java")),
                grpcPluginVersion,
                grpcPluginVersionRef,
                Optional.empty(),
                true);
    }

    private static Optional<String> versionFromAliasOrField(
            String subject,
            Optional<String> versionRef,
            Optional<String> version,
            Map<String, String> versionAliases) {
        if (versionRef.isEmpty()) {
            return version;
        }
        String alias = versionRef.orElseThrow();
        String resolved = versionAliases.get(alias);
        if (resolved == null || resolved.isBlank()) {
            throw new ZoltConfigException(
                    "Unknown versionRef `"
                            + alias
                            + "` for "
                            + subject
                            + ". Add it to [versions] or use an explicit version.");
        }
        return Optional.of(resolved);
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
            } else if (step.kind() == GeneratedSourceKind.PROTOBUF) {
                writeStringArray(toml, "inputs", step.inputs());
                step.protobuf().javaPackage().ifPresent(value -> writeAssignment(toml, "javaPackage", value));
                if (!step.protobuf().grpc()) {
                    writeAssignment(toml, "grpc", false);
                }
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

}
