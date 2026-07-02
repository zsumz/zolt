package sh.zolt.ide;

import java.nio.file.Path;
import java.util.List;

public final class WorkspaceIdeModelJsonWriter {
    private final IdeModelJsonWriter ideModelJsonWriter = new IdeModelJsonWriter();

    public String write(WorkspaceIdeModel model) {
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        field(json, 1, "schemaVersion", model.schemaVersion(), true);
        workspace(json, model.workspace());
        comma(json);
        projects(json, model.projects());
        comma(json);
        edges(json, model.edges());
        comma(json);
        diagnostics(json, model.diagnostics());
        json.append("\n}\n");
        return json.toString();
    }

    private void projects(StringBuilder json, List<WorkspaceIdeModel.ProjectModel> projects) {
        indent(json, 1).append("\"projects\": [");
        if (!projects.isEmpty()) {
            json.append('\n');
            for (int index = 0; index < projects.size(); index++) {
                WorkspaceIdeModel.ProjectModel project = projects.get(index);
                indent(json, 2).append("{\n");
                stringField(json, 3, "member", project.member(), true);
                indent(json, 3).append("\"model\": ");
                nestedJson(json, ideModelJsonWriter.write(project.model()), 3);
                indent(json, 2).append("}");
                if (index < projects.size() - 1) {
                    json.append(',');
                }
                json.append('\n');
            }
            indent(json, 1);
        }
        json.append("]");
    }

    private static void workspace(StringBuilder json, WorkspaceIdeModel.WorkspaceInfo workspace) {
        indent(json, 1).append("\"workspace\": {\n");
        stringField(json, 2, "name", workspace.name(), true);
        pathField(json, 2, "root", workspace.root(), true);
        pathField(json, 2, "config", workspace.config(), true);
        pathField(json, 2, "lockfile", workspace.lockfile(), true);
        stringArrayField(json, 2, "members", workspace.members(), true);
        stringArrayField(json, 2, "defaultMembers", workspace.defaultMembers(), true);
        stringArrayField(json, 2, "buildOrder", workspace.buildOrder(), false);
        indent(json, 1).append("}");
    }

    private static void edges(StringBuilder json, List<WorkspaceIdeModel.ProjectEdge> edges) {
        indent(json, 1).append("\"edges\": [");
        if (!edges.isEmpty()) {
            json.append('\n');
            for (int index = 0; index < edges.size(); index++) {
                WorkspaceIdeModel.ProjectEdge edge = edges.get(index);
                indent(json, 2).append("{\n");
                stringField(json, 3, "from", edge.from(), true);
                stringField(json, 3, "to", edge.to(), true);
                stringField(json, 3, "scope", edge.scope(), true);
                stringField(json, 3, "coordinate", edge.coordinate(), true);
                field(json, 3, "exported", edge.exported(), false);
                indent(json, 2).append("}");
                if (index < edges.size() - 1) {
                    json.append(',');
                }
                json.append('\n');
            }
            indent(json, 1);
        }
        json.append("]");
    }

    private static void diagnostics(StringBuilder json, List<IdeModel.Diagnostic> diagnostics) {
        indent(json, 1).append("\"diagnostics\": [");
        if (!diagnostics.isEmpty()) {
            json.append('\n');
            for (int index = 0; index < diagnostics.size(); index++) {
                IdeModel.Diagnostic diagnostic = diagnostics.get(index);
                indent(json, 2).append("{\n");
                stringField(json, 3, "severity", diagnostic.severity(), true);
                stringField(json, 3, "code", diagnostic.code(), true);
                stringField(json, 3, "message", diagnostic.message(), true);
                pathField(json, 3, "path", diagnostic.path(), true);
                stringField(json, 3, "nextStep", diagnostic.nextStep(), false);
                indent(json, 2).append("}");
                if (index < diagnostics.size() - 1) {
                    json.append(',');
                }
                json.append('\n');
            }
            indent(json, 1);
        }
        json.append("]");
    }

    private static void nestedJson(StringBuilder json, String value, int currentLevel) {
        String[] lines = value.stripTrailing().split("\\R", -1);
        if (lines.length == 0) {
            json.append("{}\n");
            return;
        }
        json.append(lines[0]).append('\n');
        for (int index = 1; index < lines.length; index++) {
            indent(json, currentLevel);
            json.append(lines[index]).append('\n');
        }
    }

    private static void stringArrayField(
            StringBuilder json,
            int level,
            String name,
            List<String> values,
            boolean trailingComma) {
        indent(json, level).append('"').append(name).append("\": [");
        if (!values.isEmpty()) {
            json.append('\n');
            for (int index = 0; index < values.size(); index++) {
                indent(json, level + 1);
                string(json, values.get(index));
                if (index < values.size() - 1) {
                    json.append(',');
                }
                json.append('\n');
            }
            indent(json, level);
        }
        json.append("]");
        if (trailingComma) {
            json.append(',');
        }
        json.append('\n');
    }

    private static void stringField(StringBuilder json, int level, String name, String value, boolean trailingComma) {
        indent(json, level).append('"').append(name).append("\": ");
        if (value == null) {
            json.append("null");
        } else {
            string(json, value);
        }
        if (trailingComma) {
            json.append(',');
        }
        json.append('\n');
    }

    private static void pathField(StringBuilder json, int level, String name, Path value, boolean trailingComma) {
        stringField(json, level, name, value == null ? null : jsonPath(value), trailingComma);
    }

    private static void field(StringBuilder json, int level, String name, int value, boolean trailingComma) {
        indent(json, level).append('"').append(name).append("\": ").append(value);
        if (trailingComma) {
            json.append(',');
        }
        json.append('\n');
    }

    private static void field(StringBuilder json, int level, String name, boolean value, boolean trailingComma) {
        indent(json, level).append('"').append(name).append("\": ").append(value);
        if (trailingComma) {
            json.append(',');
        }
        json.append('\n');
    }

    private static void string(StringBuilder json, String value) {
        json.append('"');
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"' -> json.append("\\\"");
                case '\\' -> json.append("\\\\");
                case '\b' -> json.append("\\b");
                case '\f' -> json.append("\\f");
                case '\n' -> json.append("\\n");
                case '\r' -> json.append("\\r");
                case '\t' -> json.append("\\t");
                default -> {
                    if (character < 0x20) {
                        json.append(String.format("\\u%04x", (int) character));
                    } else {
                        json.append(character);
                    }
                }
            }
        }
        json.append('"');
    }

    private static String jsonPath(Path path) {
        return path.toString().replace('\\', '/');
    }

    private static StringBuilder indent(StringBuilder json, int level) {
        return json.append("  ".repeat(level));
    }

    private static void comma(StringBuilder json) {
        json.append(",\n");
    }
}
