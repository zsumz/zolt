package sh.zolt.toml;

import sh.zolt.error.ActionableError;
import sh.zolt.toml.support.TomlValidation;
import sh.zolt.toml.support.TomlScalars;
import sh.zolt.project.ProjectMetadata;
import sh.zolt.project.VersionPolicy;
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
        TomlValidation.validateKeys("project", projectTable, PROJECT_KEYS);

        String projectVersion = TomlScalars.requiredString(projectTable, "project", "version");
        validateVersion(VersionPolicy.Context.PROJECT_VERSION, "project.version", projectVersion);
        return new ProjectMetadata(
                TomlScalars.requiredString(projectTable, "project", "name"),
                projectVersion,
                TomlScalars.requiredString(projectTable, "project", "group"),
                TomlScalars.requiredString(projectTable, "project", "java"),
                TomlScalars.optionalString(projectTable, "project", "main"));
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
            if ("project".equals(section) && result.getTable("workspace") != null) {
                throw new ZoltConfigException(ActionableError.of(
                        "This zolt.toml declares a [workspace], not a [project], so it cannot be built as a single "
                                + "project.",
                        "Re-run the command with --workspace to operate on the workspace and its members."));
            }
            throw new ZoltConfigException("Missing required section [" + section + "] in zolt.toml.");
        }
        return table;
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
