package com.zolt.toml;

import com.zolt.project.ProjectMetadata;
import com.zolt.project.VersionPolicy;
import java.util.Optional;
import java.util.Set;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;

final class ProjectSectionCodec {
    private static final Set<String> PROJECT_KEYS = Set.of("name", "version", "group", "java", "main");

    private ProjectSectionCodec() {
    }

    static ProjectMetadata defaultApplicationProject(String name, String group, String mainClass) {
        return new ProjectMetadata(name, "0.1.0", group, "21", Optional.ofNullable(blankToNull(mainClass)));
    }

    static ProjectMetadata parse(TomlParseResult result) {
        TomlTable projectTable = requiredTable(result, "project");
        validateKeys("project", projectTable, PROJECT_KEYS);

        String projectVersion = requiredString(projectTable, "project", "version");
        validateVersion(VersionPolicy.Context.PROJECT_VERSION, "project.version", projectVersion);
        return new ProjectMetadata(
                requiredString(projectTable, "project", "name"),
                projectVersion,
                requiredString(projectTable, "project", "group"),
                requiredString(projectTable, "project", "java"),
                optionalString(projectTable, "project", "main"));
    }

    static void write(StringBuilder toml, ProjectMetadata project) {
        toml.append("[project]\n");
        writeAssignment(toml, "name", project.name());
        writeAssignment(toml, "version", project.version());
        writeAssignment(toml, "group", project.group());
        writeAssignment(toml, "java", project.java());
        project.main().ifPresent(mainClass -> writeAssignment(toml, "main", mainClass));
        toml.append('\n');
    }

    private static TomlTable requiredTable(TomlParseResult result, String section) {
        TomlTable table = result.getTable(section);
        if (table == null) {
            throw new ZoltConfigException("Missing required section [" + section + "] in zolt.toml.");
        }
        return table;
    }

    private static void validateKeys(String section, TomlTable table, Set<String> allowedKeys) {
        for (String key : table.keySet()) {
            if (!allowedKeys.contains(key)) {
                throw new ZoltConfigException(
                        "Unknown field [" + section + "]." + key + " in zolt.toml. Remove it or check the spelling.");
            }
        }
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

    private static void validateVersion(
            VersionPolicy.Context context,
            String subject,
            String version) {
        VersionPolicy.violation(context, version).ifPresent(violation -> {
            throw new ZoltConfigException(
                    "Invalid "
                            + context.description()
                            + " `"
                            + version
                            + "` for ["
                            + subject
                            + "] in zolt.toml. "
                            + violation.guidance());
        });
    }

    private static void writeAssignment(StringBuilder toml, String key, String value) {
        toml.append(key).append(" = ").append(quote(value)).append('\n');
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

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
