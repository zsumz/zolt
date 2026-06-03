package com.zolt.toml;

import com.zolt.project.BuildSettings;
import com.zolt.project.NativeSettings;
import com.zolt.project.ProjectConfig;
import com.zolt.project.ProjectMetadata;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.tomlj.Toml;
import org.tomlj.TomlArray;
import org.tomlj.TomlParseError;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;

public final class ZoltTomlParser {
    private static final Set<String> TOP_LEVEL_SECTIONS = Set.of(
            "project",
            "repositories",
            "dependencies",
            "test",
            "build",
            "native");
    private static final Set<String> PROJECT_KEYS = Set.of("name", "version", "group", "java", "main");
    private static final Set<String> BUILD_KEYS = Set.of("source", "test", "output", "testOutput");
    private static final Set<String> NATIVE_KEYS = Set.of("imageName", "output", "args");

    public ProjectConfig parse(Path path) {
        try {
            return parse(Toml.parse(path));
        } catch (IOException exception) {
            throw new ZoltConfigException(
                    "Could not read zolt.toml at " + path + ". Check that the file exists and is readable.");
        }
    }

    public ProjectConfig parse(String content) {
        return parse(Toml.parse(content));
    }

    private ProjectConfig parse(TomlParseResult result) {
        if (result.hasErrors()) {
            throw new ZoltConfigException(parseErrorMessage(result));
        }

        validateTopLevelSections(result);

        TomlTable projectTable = requiredTable(result, "project");
        validateKeys("project", projectTable, PROJECT_KEYS);

        ProjectMetadata project = new ProjectMetadata(
                requiredString(projectTable, "project", "name"),
                requiredString(projectTable, "project", "version"),
                requiredString(projectTable, "project", "group"),
                requiredString(projectTable, "project", "java"),
                optionalString(projectTable, "project", "main"));

        Map<String, String> repositories = stringMap(optionalTable(result, "repositories"), "repositories");
        if (repositories.isEmpty()) {
            repositories = ProjectConfig.defaultRepositories();
        }

        Map<String, String> dependencies = stringMap(optionalTable(result, "dependencies"), "dependencies");

        TomlTable testTable = optionalTable(result, "test");
        Map<String, String> testDependencies = Map.of();
        if (testTable != null) {
            validateKeys("test", testTable, Set.of("dependencies"));
            testDependencies = stringMap(optionalTable(testTable, "dependencies"), "test.dependencies");
        }

        BuildSettings build = parseBuild(optionalTable(result, "build"));
        NativeSettings nativeSettings = parseNative(optionalTable(result, "native"), project.name());

        return new ProjectConfig(project, repositories, dependencies, testDependencies, build, nativeSettings);
    }

    private static BuildSettings parseBuild(TomlTable buildTable) {
        BuildSettings defaults = BuildSettings.defaults();
        if (buildTable == null) {
            return defaults;
        }

        validateKeys("build", buildTable, BUILD_KEYS);
        return new BuildSettings(
                stringOrDefault(buildTable, "build", "source", defaults.source()),
                stringOrDefault(buildTable, "build", "test", defaults.test()),
                stringOrDefault(buildTable, "build", "output", defaults.output()),
                stringOrDefault(buildTable, "build", "testOutput", defaults.testOutput()));
    }

    private static NativeSettings parseNative(TomlTable nativeTable, String projectName) {
        if (nativeTable == null) {
            return NativeSettings.defaults();
        }

        NativeSettings defaults = NativeSettings.defaults().withDefaultImageName(projectName);
        validateKeys("native", nativeTable, NATIVE_KEYS);
        return new NativeSettings(
                stringOrDefault(nativeTable, "native", "imageName", defaults.imageName()),
                stringOrDefault(nativeTable, "native", "output", defaults.output()),
                stringListOrDefault(nativeTable, "native", "args", defaults.args()));
    }

    private static String parseErrorMessage(TomlParseResult result) {
        TomlParseError firstError = result.errors().getFirst();
        return "Could not parse zolt.toml. Fix the TOML syntax near "
                + firstError.position()
                + ": "
                + firstError.getMessage();
    }

    private static void validateTopLevelSections(TomlParseResult result) {
        for (String key : result.keySet()) {
            if (!TOP_LEVEL_SECTIONS.contains(key)) {
                throw new ZoltConfigException(
                        "Unknown top-level section [" + key + "] in zolt.toml. Remove it or check the spelling.");
            }
        }
    }

    private static void validateKeys(String section, TomlTable table, Set<String> allowedKeys) {
        for (String key : table.keySet()) {
            if (!allowedKeys.contains(key)) {
                throw new ZoltConfigException(
                        "Unknown field [" + section + "]." + key + " in zolt.toml. Remove it or check the spelling.");
            }
        }
    }

    private static TomlTable requiredTable(TomlParseResult result, String section) {
        TomlTable table = result.getTable(section);
        if (table == null) {
            throw new ZoltConfigException("Missing required section [" + section + "] in zolt.toml.");
        }
        return table;
    }

    private static TomlTable optionalTable(TomlParseResult result, String section) {
        return result.getTable(section);
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

    private static String stringOrDefault(TomlTable table, String section, String key, String defaultValue) {
        Object rawValue = table.get(List.of(key));
        if (rawValue == null) {
            return defaultValue;
        }
        if (!(rawValue instanceof String value)) {
            throw new ZoltConfigException(
                    "Invalid value for [" + section + "]." + key + " in zolt.toml. Use a non-empty string value.");
        }
        if (value.isBlank()) {
            return defaultValue;
        }
        return value;
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
}
