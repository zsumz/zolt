package sh.zolt.explain.gradle;

import static sh.zolt.explain.gradle.GradleExplainJsonFields.indent;
import static sh.zolt.explain.gradle.GradleExplainJsonFields.path;
import static sh.zolt.explain.gradle.GradleExplainJsonFields.stringArrayField;
import static sh.zolt.explain.gradle.GradleExplainJsonFields.stringField;

import java.util.List;
import java.util.Optional;

final class GradleExplainProjectJsonWriter {
    void projects(StringBuilder json, List<GradleProjectInspection> projects) {
        indent(json, 1).append("\"projects\": [");
        if (!projects.isEmpty()) {
            json.append('\n');
            for (int index = 0; index < projects.size(); index++) {
                GradleProjectInspection project = projects.get(index);
                indent(json, 2).append("{\n");
                stringField(json, 3, "path", path(project.path()), true);
                stringField(json, 3, "name", project.name(), true);
                stringField(json, 3, "buildFile", project.buildFile(), true);
                stringField(json, 3, "dsl", project.dsl(), true);
                stringField(json, 3, "javaVersion", project.javaVersion(), true);
                optionalStringField(json, 3, "group", project.group(), true);
                optionalStringField(json, 3, "version", project.version(), true);
                optionalStringField(json, 3, "mainClass", project.mainClass(), true);
                pluginArray(json, 3, "plugins", project.plugins(), true);
                repositoryArray(json, 3, "repositories", project.repositories(), true);
                dependencyArray(json, 3, "dependencies", project.dependencies(), true);
                stringArrayField(json, 3, "sourceRoots", project.sourceRoots(), true);
                stringArrayField(json, 3, "testSourceRoots", project.testSourceRoots(), false);
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

    private static void optionalStringField(
            StringBuilder json,
            int level,
            String name,
            Optional<String> value,
            boolean trailingComma) {
        value.filter(candidate -> !candidate.isBlank())
                .ifPresent(candidate -> stringField(json, level, name, candidate, trailingComma));
    }

    private static void pluginArray(
            StringBuilder json,
            int level,
            String name,
            List<GradlePluginInspection> plugins,
            boolean trailingComma) {
        indent(json, level).append('"').append(name).append("\": [");
        if (!plugins.isEmpty()) {
            json.append('\n');
            for (int index = 0; index < plugins.size(); index++) {
                GradlePluginInspection plugin = plugins.get(index);
                indent(json, level + 1).append("{\n");
                stringField(json, level + 2, "id", plugin.id(), true);
                stringField(json, level + 2, "version", plugin.version(), false);
                indent(json, level + 1).append("}");
                if (index < plugins.size() - 1) {
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

    private static void repositoryArray(
            StringBuilder json,
            int level,
            String name,
            List<GradleRepositoryInspection> repositories,
            boolean trailingComma) {
        indent(json, level).append('"').append(name).append("\": [");
        if (!repositories.isEmpty()) {
            json.append('\n');
            for (int index = 0; index < repositories.size(); index++) {
                GradleRepositoryInspection repository = repositories.get(index);
                indent(json, level + 1).append("{\n");
                stringField(json, level + 2, "kind", repository.kind(), true);
                stringField(json, level + 2, "url", repository.url(), false);
                indent(json, level + 1).append("}");
                if (index < repositories.size() - 1) {
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

    private static void dependencyArray(
            StringBuilder json,
            int level,
            String name,
            List<GradleDependencyInspection> dependencies,
            boolean trailingComma) {
        indent(json, level).append('"').append(name).append("\": [");
        if (!dependencies.isEmpty()) {
            json.append('\n');
            for (int index = 0; index < dependencies.size(); index++) {
                GradleDependencyInspection dependency = dependencies.get(index);
                indent(json, level + 1).append("{\n");
                stringField(json, level + 2, "configuration", dependency.configuration(), true);
                stringField(json, level + 2, "notation", dependency.notation(), true);
                stringField(json, level + 2, "resolvedCoordinate", dependency.resolvedCoordinate(), true);
                stringField(json, level + 2, "versionCatalogAlias", dependency.versionCatalogAlias(), false);
                indent(json, level + 1).append("}");
                if (index < dependencies.size() - 1) {
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
}
