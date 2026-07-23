package sh.zolt.toml.generated;

import sh.zolt.project.ExecGenerationSettings;
import sh.zolt.project.ExecToolCoordinate;
import sh.zolt.project.ExecToolSettings;
import sh.zolt.project.GeneratedSourceKind;
import sh.zolt.project.GeneratedSourceStep;
import sh.zolt.project.OpenApiGenerationSettings;
import sh.zolt.project.ProducesLane;
import sh.zolt.project.ProtobufGenerationSettings;
import sh.zolt.project.VersionPolicy;
import sh.zolt.toml.ZoltConfigException;
import sh.zolt.toml.support.TomlScalars;
import sh.zolt.toml.support.TomlValidation;
import sh.zolt.toml.support.TomlVersions;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.tomlj.TomlArray;
import org.tomlj.TomlTable;

final class ExecGeneratedSourceParser {
    /** The built-in {@code tool = "project"} pseudo-tool: never declared, always synthesized. */
    static final String PROJECT_TOOL = "project";

    private static final Set<String> EXEC_TOOL_KEYS = Set.of(
            "runner", "coordinates", "mainClass", "binary", "versionCommand", "versionExpect", "allowUnpinnedTool");
    private static final Set<String> EXEC_COORDINATE_KEYS = Set.of("coordinate", "version", "versionRef");
    private static final Set<String> GENERATED_EXEC_SOURCE_KEYS = Set.of(
            "kind", "language", "tool", "mainClass", "args", "inputs", "output", "produces", "into", "cache", "env",
            "secretEnv", "inheritEnv", "cwd", "timeoutSeconds", "required", "clean");

    private ExecGeneratedSourceParser() {
    }

    static Map<String, ExecToolSettings> parseTools(TomlTable table, Map<String, String> versionAliases) {
        if (table == null) {
            return Map.of();
        }
        Map<String, ExecToolSettings> tools = new LinkedHashMap<>();
        for (String name : table.keySet()) {
            if (PROJECT_TOOL.equals(name)) {
                throw new ZoltConfigException(
                        "Exec tool name `project` is reserved for the built-in pseudo-tool in [generated.execTools]. "
                                + "Reference it directly with tool = \"project\"; do not declare it.");
            }
            TomlTable toolTable = optionalTable(table, name);
            if (toolTable == null) {
                throw new ZoltConfigException(
                        "Invalid value for [generated.execTools]." + name
                                + " in zolt.toml. Use a table with runner, and either jvm (coordinates, mainClass) or "
                                + "process (binary, versionCommand) fields.");
            }
            String section = "generated.execTools." + name;
            TomlValidation.validateKeysWithVersionRefHint(section, toolTable, EXEC_TOOL_KEYS);
            String runner = TomlScalars.requiredString(toolTable, section, "runner");
            tools.put(name, switch (runner) {
                case "jvm" -> parseJvmTool(toolTable, section, versionAliases);
                case "process" -> parseProcessTool(toolTable, section);
                default -> throw new ZoltConfigException(
                        "Unsupported exec tool runner `" + runner + "` in [" + section
                                + "]. Supported runners are: jvm, process. (tool = \"project\" is a built-in "
                                + "pseudo-tool referenced directly, never declared.)");
            });
        }
        return Map.copyOf(tools);
    }

    private static ExecToolSettings parseJvmTool(
            TomlTable toolTable, String section, Map<String, String> versionAliases) {
        List<ExecToolCoordinate> coordinates = parseCoordinates(toolTable, section, versionAliases);
        String mainClass = TomlScalars.requiredString(toolTable, section, "mainClass");
        return new ExecToolSettings("jvm", coordinates, mainClass);
    }

    private static ExecToolSettings parseProcessTool(TomlTable toolTable, String section) {
        String binary = TomlScalars.requiredString(toolTable, section, "binary");
        List<String> versionCommand = TomlScalars.stringListOrDefault(toolTable, section, "versionCommand", List.of());
        if (versionCommand.isEmpty()) {
            throw new ZoltConfigException(
                    "Missing required field [" + section + "].versionCommand in zolt.toml. Add the argv probe whose "
                            + "stdout identifies the tool, e.g. versionCommand = [\"" + binary + "\", \"--version\"].");
        }
        Optional<String> versionExpect = TomlScalars.optionalString(toolTable, section, "versionExpect");
        boolean allowUnpinnedTool = TomlScalars.booleanOrDefault(toolTable, section, "allowUnpinnedTool", false);
        return ExecToolSettings.process(binary, versionCommand, versionExpect, allowUnpinnedTool);
    }

    static GeneratedSourceStep parseStep(
            String id,
            TomlTable table,
            String section,
            Map<String, ExecToolSettings> tools) {
        TomlValidation.validateKeysWithVersionRefHint(section, table, GENERATED_EXEC_SOURCE_KEYS);
        String language = TomlScalars.stringOrDefault(table, section, "language", "java");
        if (!"java".equals(language)) {
            throw new ZoltConfigException(
                    "Unsupported generated source language `" + language
                            + "` in zolt.toml. Supported generated source languages are: java.");
        }
        String toolName = TomlScalars.requiredString(table, section, "tool");
        ExecToolSettings tool = resolveTool(toolName, table, section, tools);
        List<String> inputs = TomlScalars.stringListOrDefault(table, section, "inputs", List.of());
        if (inputs.isEmpty()) {
            throw new ZoltConfigException(
                    "Missing required field [" + section
                            + "].inputs in zolt.toml. Add at least one project-relative input path.");
        }
        List<String> args = TomlScalars.stringListOrDefault(table, section, "args", List.of());
        String producesValue = TomlScalars.requiredString(table, section, "produces");
        ProducesLane produces = ProducesLane.fromConfigValue(producesValue)
                .orElseThrow(() -> new ZoltConfigException(
                        "Unsupported exec produces lane `" + producesValue + "` in [" + section
                                + "]. Supported lanes are: " + ProducesLane.supportedValues() + "."));
        Optional<String> into = TomlScalars.optionalString(table, section, "into");
        if (into.isPresent() && produces != ProducesLane.RESOURCES && produces != ProducesLane.TEST_RESOURCES) {
            throw new ZoltConfigException(
                    "[" + section + "].into applies only to produces = \"resources\" or \"test-resources\". "
                            + "Remove into or set a resource-producing lane.");
        }
        String cache = TomlScalars.stringOrDefault(table, section, "cache", "content");
        if (!"content".equals(cache) && !"none".equals(cache)) {
            throw new ZoltConfigException(
                    "Unsupported cache policy `" + cache + "` for [" + section
                            + "]. Supported cache policies are: content, none.");
        }
        Map<String, String> env = TomlScalars.stringMap(optionalTable(table, "env"), section + ".env");
        Map<String, String> secretEnv = TomlScalars.stringMap(optionalTable(table, "secretEnv"), section + ".secretEnv");
        List<String> inheritEnv = TomlScalars.stringListOrDefault(table, section, "inheritEnv", List.of());
        Optional<String> cwd = TomlScalars.optionalString(table, section, "cwd");
        int timeoutSeconds = TomlScalars.integerOrDefault(
                table, section, "timeoutSeconds", ExecGenerationSettings.DEFAULT_TIMEOUT_SECONDS);
        if (timeoutSeconds <= 0) {
            throw new ZoltConfigException(
                    "[" + section + "].timeoutSeconds must be a positive number of seconds.");
        }
        ExecGenerationSettings settings = new ExecGenerationSettings(
                toolName, tool, args, produces, into, env, cache, cwd, secretEnv, inheritEnv, timeoutSeconds);
        return new GeneratedSourceStep(
                id,
                GeneratedSourceKind.EXEC,
                language,
                TomlScalars.requiredString(table, section, "output"),
                inputs,
                TomlScalars.booleanOrDefault(table, section, "required", true),
                TomlScalars.booleanOrDefault(table, section, "clean", true),
                OpenApiGenerationSettings.empty(),
                ProtobufGenerationSettings.empty(),
                settings);
    }

    private static ExecToolSettings resolveTool(
            String toolName,
            TomlTable table,
            String section,
            Map<String, ExecToolSettings> tools) {
        if (PROJECT_TOOL.equals(toolName)) {
            String mainClass = TomlScalars.requiredString(table, section, "mainClass");
            return ExecToolSettings.project(mainClass);
        }
        if (TomlScalars.optionalString(table, section, "mainClass").isPresent()) {
            throw new ZoltConfigException(
                    "[" + section + "].mainClass applies only to tool = \"project\". "
                            + "Declare the main class on [generated.execTools." + toolName + "] instead.");
        }
        ExecToolSettings tool = tools.get(toolName);
        if (tool == null) {
            throw new ZoltConfigException(
                    "Unknown exec tool `" + toolName + "` in [" + section
                            + "]. Add [generated.execTools." + toolName + "] or fix the tool reference.");
        }
        return tool;
    }

    private static List<ExecToolCoordinate> parseCoordinates(
            TomlTable toolTable,
            String section,
            Map<String, String> versionAliases) {
        Object rawValue = toolTable.get(List.of("coordinates"));
        if (rawValue == null) {
            throw new ZoltConfigException(
                    "Missing required field [" + section
                            + "].coordinates in zolt.toml. Add at least one { coordinate, version|versionRef } entry.");
        }
        if (!(rawValue instanceof TomlArray array)) {
            throw new ZoltConfigException(
                    "Invalid value for [" + section
                            + "].coordinates in zolt.toml. Use an array of { coordinate, version|versionRef } tables.");
        }
        List<ExecToolCoordinate> coordinates = new ArrayList<>();
        for (int index = 0; index < array.size(); index++) {
            if (!(array.get(index) instanceof TomlTable coordinateTable)) {
                throw new ZoltConfigException(
                        "Invalid value for [" + section + "].coordinates[" + index
                                + "] in zolt.toml. Use { coordinate = \"group:artifact\", versionRef = \"alias\" }.");
            }
            String coordinateSection = section + ".coordinates[" + index + "]";
            TomlValidation.validateKeysWithVersionRefHint(coordinateSection, coordinateTable, EXEC_COORDINATE_KEYS);
            String coordinate = TomlScalars.requiredString(coordinateTable, coordinateSection, "coordinate");
            Optional<String> version = TomlVersions.optionalVersionOrRef(
                    coordinateTable, coordinateSection, VersionPolicy.Context.TOOL_DEPENDENCY, versionAliases);
            if (version.isEmpty()) {
                throw new ZoltConfigException(
                        "Missing version for [" + coordinateSection
                                + "] in zolt.toml. Add version or versionRef.");
            }
            Optional<String> versionRef = TomlVersions.optionalVersionRef(coordinateTable, coordinateSection, versionAliases);
            coordinates.add(new ExecToolCoordinate(coordinate, version, versionRef));
        }
        return List.copyOf(coordinates);
    }

    private static TomlTable optionalTable(TomlTable table, String key) {
        return table.getTable(List.of(key));
    }
}
