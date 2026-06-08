package com.zolt.ide;

import java.nio.file.Path;
import java.util.List;

public final class IdeModelJsonWriter {
    public String write(IdeModel model) {
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        field(json, 1, "schemaVersion", model.schemaVersion(), true);
        project(json, model.project());
        comma(json);
        java(json, model.java());
        comma(json);
        paths(json, model.paths());
        comma(json);
        sourceRoots(json, model.sourceRoots());
        comma(json);
        resourceRoots(json, model.resourceRoots());
        comma(json);
        outputs(json, model.outputs());
        comma(json);
        dependencies(json, model.dependencies());
        comma(json);
        classpaths(json, model.classpaths());
        comma(json);
        diagnostics(json, model.diagnostics());
        json.append("\n}\n");
        return json.toString();
    }

    private static void project(StringBuilder json, IdeModel.ProjectInfo project) {
        indent(json, 1).append("\"project\": {\n");
        stringField(json, 2, "name", project.name(), true);
        stringField(json, 2, "group", project.group(), true);
        stringField(json, 2, "version", project.version(), true);
        stringField(json, 2, "mainClass", project.mainClass(), false);
        indent(json, 1).append("}");
    }

    private static void java(StringBuilder json, IdeModel.JavaInfo java) {
        indent(json, 1).append("\"java\": {\n");
        stringField(json, 2, "version", java.version(), true);
        stringField(json, 2, "detectedVersion", java.detectedVersion(), true);
        stringField(json, 2, "javaHome", java.javaHome(), false);
        indent(json, 1).append("}");
    }

    private static void paths(StringBuilder json, IdeModel.PathInfo paths) {
        indent(json, 1).append("\"paths\": {\n");
        pathField(json, 2, "root", paths.root(), true);
        pathField(json, 2, "config", paths.config(), true);
        pathField(json, 2, "lockfile", paths.lockfile(), false);
        indent(json, 1).append("}");
    }

    private static void sourceRoots(StringBuilder json, List<IdeModel.SourceRoot> roots) {
        indent(json, 1).append("\"sourceRoots\": [");
        if (!roots.isEmpty()) {
            json.append('\n');
            for (int index = 0; index < roots.size(); index++) {
                IdeModel.SourceRoot root = roots.get(index);
                indent(json, 2).append("{\n");
                stringField(json, 3, "id", root.id(), true);
                stringField(json, 3, "kind", root.kind(), true);
                stringField(json, 3, "language", root.language(), true);
                pathField(json, 3, "path", root.path(), true);
                field(json, 3, "generated", root.generated(), false);
                indent(json, 2).append("}");
                if (index < roots.size() - 1) {
                    json.append(',');
                }
                json.append('\n');
            }
            indent(json, 1);
        }
        json.append("]");
    }

    private static void resourceRoots(StringBuilder json, List<IdeModel.ResourceRoot> roots) {
        indent(json, 1).append("\"resourceRoots\": [");
        if (!roots.isEmpty()) {
            json.append('\n');
            for (int index = 0; index < roots.size(); index++) {
                IdeModel.ResourceRoot root = roots.get(index);
                indent(json, 2).append("{\n");
                stringField(json, 3, "id", root.id(), true);
                stringField(json, 3, "kind", root.kind(), true);
                pathField(json, 3, "path", root.path(), false);
                indent(json, 2).append("}");
                if (index < roots.size() - 1) {
                    json.append(',');
                }
                json.append('\n');
            }
            indent(json, 1);
        }
        json.append("]");
    }

    private static void outputs(StringBuilder json, IdeModel.OutputInfo outputs) {
        indent(json, 1).append("\"outputs\": {\n");
        pathField(json, 2, "mainClasses", outputs.mainClasses(), true);
        pathField(json, 2, "testClasses", outputs.testClasses(), true);
        pathField(json, 2, "package", outputs.packagePath(), false);
        indent(json, 1).append("}");
    }

    private static void dependencies(StringBuilder json, IdeModel.DependencyInfo dependencies) {
        indent(json, 1).append("\"dependencies\": {\n");
        dependencyArrayField(json, 2, "api", dependencies.api(), true);
        dependencyArrayField(json, 2, "implementation", dependencies.implementation(), true);
        dependencyArrayField(json, 2, "runtime", dependencies.runtime(), true);
        dependencyArrayField(json, 2, "provided", dependencies.provided(), true);
        dependencyArrayField(json, 2, "dev", dependencies.dev(), true);
        dependencyArrayField(json, 2, "test", dependencies.test(), true);
        dependencyArrayField(json, 2, "annotationProcessors", dependencies.annotationProcessors(), true);
        dependencyArrayField(json, 2, "testAnnotationProcessors", dependencies.testAnnotationProcessors(), false);
        indent(json, 1).append("}");
    }

    private static void classpaths(StringBuilder json, IdeModel.ClasspathInfo classpaths) {
        indent(json, 1).append("\"classpaths\": {\n");
        pathArrayField(json, 2, "compile", classpaths.compile(), true);
        pathArrayField(json, 2, "runtime", classpaths.runtime(), true);
        pathArrayField(json, 2, "test", classpaths.test(), false);
        indent(json, 1).append("}");
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

    private static void pathArrayField(
            StringBuilder json,
            int level,
            String name,
            List<Path> values,
            boolean trailingComma) {
        indent(json, level).append('"').append(name).append("\": [");
        if (!values.isEmpty()) {
            json.append('\n');
            for (int index = 0; index < values.size(); index++) {
                indent(json, level + 1);
                string(json, jsonPath(values.get(index)));
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

    private static void dependencyArrayField(
            StringBuilder json,
            int level,
            String name,
            List<IdeModel.DependencyDeclaration> values,
            boolean trailingComma) {
        indent(json, level).append('"').append(name).append("\": [");
        if (!values.isEmpty()) {
            json.append('\n');
            for (int index = 0; index < values.size(); index++) {
                IdeModel.DependencyDeclaration dependency = values.get(index);
                indent(json, level + 1).append("{\n");
                stringField(json, level + 2, "coordinate", dependency.coordinate(), true);
                stringField(json, level + 2, "version", dependency.version(), true);
                field(json, level + 2, "managed", dependency.managed(), true);
                stringField(json, level + 2, "workspace", dependency.workspace(), false);
                indent(json, level + 1).append("}");
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
