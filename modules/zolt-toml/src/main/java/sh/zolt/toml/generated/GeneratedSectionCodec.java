package sh.zolt.toml.generated;

import sh.zolt.project.BuildSettings;
import sh.zolt.project.ExecGenerationSettings;
import sh.zolt.project.ExecToolCoordinate;
import sh.zolt.project.ExecToolSettings;
import sh.zolt.project.GeneratedSourceKind;
import sh.zolt.project.GeneratedSourceStep;
import sh.zolt.project.OpenApiGenerationSettings;
import sh.zolt.project.ProtobufGenerationSettings;
import sh.zolt.toml.ZoltConfigException;
import sh.zolt.toml.support.TomlScalars;
import sh.zolt.toml.support.TomlValidation;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import org.tomlj.TomlTable;

public final class GeneratedSectionCodec {
    private static final Set<String> GENERATED_KEYS =
            Set.of("main", "test", "openapiTool", "openapiPresets", "protobufTool", "execTools");
    private static final Set<String> GENERATED_DECLARED_SOURCE_KEYS =
            Set.of("kind", "language", "output", "inputs", "required", "clean");

    private GeneratedSectionCodec() {
    }

    public static BuildSettings parse(
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
        ProtobufGenerationSettings protobufTool =
                ProtobufGeneratedSourceParser.parseTool(optionalTable(table, "protobufTool"), versionAliases);
        Map<String, ExecToolSettings> execTools =
                ExecGeneratedSourceParser.parseTools(optionalTable(table, "execTools"), versionAliases);
        return build.withGeneratedSources(
                parseGeneratedSourceScope(
                        optionalTable(table, "main"), "generated.main", tool, presets, protobufTool, execTools),
                parseGeneratedSourceScope(
                        optionalTable(table, "test"), "generated.test", tool, presets, protobufTool, execTools));
    }

    public static void write(StringBuilder toml, BuildSettings build) {
        writeOpenApiTool(toml, build);
        writeExecTools(toml, build);
        writeGeneratedSourceScope(toml, "generated.main", build.generatedMainSources());
        writeGeneratedSourceScope(toml, "generated.test", build.generatedTestSources());
    }

    private static List<GeneratedSourceStep> parseGeneratedSourceScope(
            TomlTable table,
            String section,
            OpenApiGeneratedSourceParser.Tool tool,
            Map<String, OpenApiGenerationSettings> presets,
            ProtobufGenerationSettings protobufTool,
            Map<String, ExecToolSettings> execTools) {
        if (table == null) {
            return List.of();
        }
        List<GeneratedSourceStep> steps = new ArrayList<>();
        for (String id : table.keySet()) {
            Object rawStep = table.get(List.of(id));
            if (!(rawStep instanceof TomlTable stepTable)) {
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
            if (kind == GeneratedSourceKind.EXEC) {
                steps.add(ExecGeneratedSourceParser.parseStep(id, stepTable, stepSection, execTools));
                continue;
            }
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
                steps.add(ProtobufGeneratedSourceParser.parseStep(id, stepTable, stepSection, protobufTool));
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
            } else if (step.kind() == GeneratedSourceKind.EXEC) {
                writeExecSettings(toml, step);
            } else {
                writeStringArray(toml, "inputs", step.inputs());
            }
            if (!step.required()) {
                writeAssignment(toml, "required", false);
            }
            boolean cleanDefaultsTrue = step.kind() == GeneratedSourceKind.OPENAPI
                    || step.kind() == GeneratedSourceKind.EXEC;
            if (cleanDefaultsTrue && !step.clean()) {
                writeAssignment(toml, "clean", false);
            } else if (!cleanDefaultsTrue && step.clean()) {
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

    private static void writeExecTools(StringBuilder toml, BuildSettings build) {
        Map<String, ExecToolSettings> tools = new TreeMap<>();
        java.util.stream.Stream
                .concat(build.generatedMainSources().stream(), build.generatedTestSources().stream())
                .filter(step -> step.kind() == GeneratedSourceKind.EXEC)
                .forEach(step -> tools.putIfAbsent(step.exec().toolName(), step.exec().tool()));
        for (Map.Entry<String, ExecToolSettings> entry : tools.entrySet()) {
            ExecToolSettings tool = entry.getValue();
            toml.append("\n[generated.execTools.").append(entry.getKey()).append("]\n");
            writeAssignment(toml, "runner", tool.runner());
            writeExecCoordinates(toml, tool.coordinates());
            writeAssignment(toml, "mainClass", tool.mainClass());
        }
    }

    private static void writeExecCoordinates(StringBuilder toml, List<ExecToolCoordinate> coordinates) {
        toml.append("coordinates = [\n");
        for (ExecToolCoordinate coordinate : coordinates) {
            toml.append("    { coordinate = ").append(quote(coordinate.coordinate()));
            if (coordinate.versionRef().isPresent()) {
                toml.append(", versionRef = ").append(quote(coordinate.versionRef().orElseThrow()));
            } else {
                coordinate.version().ifPresent(version -> toml.append(", version = ").append(quote(version)));
            }
            toml.append(" },\n");
        }
        toml.append("]\n");
    }

    private static void writeExecSettings(StringBuilder toml, GeneratedSourceStep step) {
        ExecGenerationSettings exec = step.exec();
        writeAssignment(toml, "tool", exec.toolName());
        if (!exec.args().isEmpty()) {
            writeStringArray(toml, "args", exec.args());
        }
        writeStringArray(toml, "inputs", step.inputs());
        writeAssignment(toml, "produces", exec.produces().configValue());
        exec.into().ifPresent(value -> writeAssignment(toml, "into", value));
        if (!"content".equals(exec.cache())) {
            writeAssignment(toml, "cache", exec.cache());
        }
        if (!exec.env().isEmpty()) {
            writeInlineStringMap(toml, "env", exec.env());
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
