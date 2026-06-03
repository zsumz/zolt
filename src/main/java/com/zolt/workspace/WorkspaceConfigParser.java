package com.zolt.workspace;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.tomlj.Toml;
import org.tomlj.TomlArray;
import org.tomlj.TomlParseError;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;

public final class WorkspaceConfigParser {
    public static final String WORKSPACE_FILE = "zolt-workspace.toml";

    private static final Set<String> TOP_LEVEL_SECTIONS = Set.of("workspace", "repositories", "platforms");
    private static final Set<String> WORKSPACE_KEYS = Set.of("name", "members", "defaultMembers");

    public WorkspaceConfig parse(Path path) {
        try {
            return parse(Toml.parse(path));
        } catch (IOException exception) {
            throw new WorkspaceConfigException(
                    "Could not read zolt-workspace.toml at " + path + ". Check that the file exists and is readable.");
        }
    }

    public WorkspaceConfig parse(String content) {
        return parse(Toml.parse(content));
    }

    private WorkspaceConfig parse(TomlParseResult result) {
        if (result.hasErrors()) {
            throw new WorkspaceConfigException(parseErrorMessage(result));
        }
        validateTopLevelSections(result);

        TomlTable workspaceTable = requiredTable(result, "workspace");
        validateKeys("workspace", workspaceTable, WORKSPACE_KEYS);

        return new WorkspaceConfig(
                requiredString(workspaceTable, "workspace", "name"),
                requiredStringList(workspaceTable, "workspace", "members"),
                stringListOrDefault(workspaceTable, "workspace", "defaultMembers", List.of()),
                stringMap(optionalTable(result, "repositories"), "repositories"),
                stringMap(optionalTable(result, "platforms"), "platforms"));
    }

    private static String parseErrorMessage(TomlParseResult result) {
        TomlParseError firstError = result.errors().getFirst();
        return "Could not parse zolt-workspace.toml. Fix the TOML syntax near "
                + firstError.position()
                + ": "
                + firstError.getMessage();
    }

    private static void validateTopLevelSections(TomlParseResult result) {
        for (String key : result.keySet()) {
            if (!TOP_LEVEL_SECTIONS.contains(key)) {
                throw new WorkspaceConfigException(
                        "Unknown top-level section [" + key + "] in zolt-workspace.toml. Remove it or check the spelling.");
            }
        }
    }

    private static void validateKeys(String section, TomlTable table, Set<String> allowedKeys) {
        for (String key : table.keySet()) {
            if (!allowedKeys.contains(key)) {
                throw new WorkspaceConfigException(
                        "Unknown field [" + section + "]." + key + " in zolt-workspace.toml. Remove it or check the spelling.");
            }
        }
    }

    private static TomlTable requiredTable(TomlParseResult result, String section) {
        TomlTable table = result.getTable(section);
        if (table == null) {
            throw new WorkspaceConfigException("Missing required section [" + section + "] in zolt-workspace.toml.");
        }
        return table;
    }

    private static TomlTable optionalTable(TomlParseResult result, String section) {
        return result.getTable(section);
    }

    private static String requiredString(TomlTable table, String section, String key) {
        String value = table.getString(key);
        if (value == null || value.isBlank()) {
            throw new WorkspaceConfigException(
                    "Missing required field [" + section + "]." + key + " in zolt-workspace.toml. Add a non-empty string value.");
        }
        return value;
    }

    private static List<String> requiredStringList(TomlTable table, String section, String key) {
        List<String> values = stringListOrDefault(table, section, key, null);
        if (values == null) {
            throw new WorkspaceConfigException(
                    "Missing required field [" + section + "]." + key + " in zolt-workspace.toml. Add an array of member paths.");
        }
        if (values.isEmpty()) {
            throw new WorkspaceConfigException(
                    "Invalid value for [" + section + "]." + key + " in zolt-workspace.toml. Add at least one member path.");
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
            throw new WorkspaceConfigException(
                    "Invalid value for [" + section + "]." + key + " in zolt-workspace.toml. Use an array of strings.");
        }

        ArrayList<String> values = new ArrayList<>();
        LinkedHashSet<String> uniqueValues = new LinkedHashSet<>();
        for (int index = 0; index < array.size(); index++) {
            String value = array.getString(index);
            if (value == null || value.isBlank()) {
                throw new WorkspaceConfigException(
                        "Invalid value for [" + section + "]." + key + "[" + index + "] in zolt-workspace.toml. Use a non-empty string.");
            }
            if (!uniqueValues.add(value)) {
                throw new WorkspaceConfigException(
                        "Duplicate value `" + value + "` in [" + section + "]." + key + " in zolt-workspace.toml.");
            }
            values.add(value);
        }
        return List.copyOf(values);
    }

    private static Map<String, String> stringMap(TomlTable table, String section) {
        if (table == null) {
            return Map.of();
        }

        Map<String, String> values = new LinkedHashMap<>();
        for (String key : table.keySet()) {
            Object rawValue = table.get(List.of(key));
            if (!(rawValue instanceof String value) || value.isBlank()) {
                throw new WorkspaceConfigException(
                        "Invalid value for [" + section + "]." + key + " in zolt-workspace.toml. Use a non-empty string value.");
            }
            values.put(key, value);
        }
        return values;
    }
}
