package com.zolt.explain.maven;

import static com.zolt.explain.maven.MavenExplainJsonFields.booleanField;
import static com.zolt.explain.maven.MavenExplainJsonFields.indent;
import static com.zolt.explain.maven.MavenExplainJsonFields.path;
import static com.zolt.explain.maven.MavenExplainJsonFields.stringArrayField;
import static com.zolt.explain.maven.MavenExplainJsonFields.stringField;

import java.util.List;

final class MavenExplainProjectJsonWriter {
    void projects(StringBuilder json, List<MavenProjectInspection> projects) {
        indent(json, 1).append("\"projects\": [");
        if (!projects.isEmpty()) {
            json.append('\n');
            for (int index = 0; index < projects.size(); index++) {
                MavenProjectInspection project = projects.get(index);
                indent(json, 2).append("{\n");
                stringField(json, 3, "path", path(project.path()), true);
                // "name" stays the Maven artifactId (also the emitted Zolt project name) for schema
                // compatibility; the human <name> element is surfaced additively as "displayName".
                stringField(json, 3, "name", project.artifactId(), true);
                stringField(json, 3, "groupId", project.groupId(), true);
                stringField(json, 3, "version", project.version(), true);
                stringField(json, 3, "displayName", project.name(), true);
                stringField(json, 3, "packaging", project.packaging(), true);
                stringField(json, 3, "javaVersion", project.javaVersion(), true);
                stringArrayField(json, 3, "modules", project.modules(), true);
                stringArrayField(json, 3, "sourceRoots", project.sourceRoots(), true);
                stringArrayField(json, 3, "testSourceRoots", project.testSourceRoots(), true);
                stringArrayField(json, 3, "resourceRoots", project.resourceRoots(), true);
                dependencyArray(json, 3, "dependencies", project.dependencies(), true);
                dependencyArray(json, 3, "dependencyManagement", project.dependencyManagement(), true);
                dependencyArray(json, 3, "importedBoms", project.importedBoms(), true);
                annotationProcessorArray(json, 3, "annotationProcessors", project.annotationProcessors(), true);
                pluginArray(json, 3, "plugins", project.plugins(), true);
                profileArray(json, 3, "profiles", project.profiles(), false);
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

    private static void dependencyArray(
            StringBuilder json,
            int level,
            String name,
            List<MavenDependencyInspection> dependencies,
            boolean trailingComma) {
        indent(json, level).append('"').append(name).append("\": [");
        if (!dependencies.isEmpty()) {
            json.append('\n');
            for (int index = 0; index < dependencies.size(); index++) {
                MavenDependencyInspection dependency = dependencies.get(index);
                indent(json, level + 1).append("{\n");
                stringField(json, level + 2, "scope", dependency.scope(), true);
                stringField(json, level + 2, "coordinate", dependency.coordinate(), true);
                stringField(json, level + 2, "version", dependency.version(), true);
                stringField(json, level + 2, "type", dependency.type(), true);
                booleanField(json, level + 2, "optional", dependency.optional(), true);
                booleanField(json, level + 2, "managed", dependency.managed(), true);
                booleanField(json, level + 2, "importedBom", dependency.importedBom(), false);
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

    private static void annotationProcessorArray(
            StringBuilder json,
            int level,
            String name,
            List<MavenAnnotationProcessorInspection> processors,
            boolean trailingComma) {
        indent(json, level).append('"').append(name).append("\": [");
        if (!processors.isEmpty()) {
            json.append('\n');
            for (int index = 0; index < processors.size(); index++) {
                MavenAnnotationProcessorInspection processor = processors.get(index);
                indent(json, level + 1).append("{\n");
                stringField(json, level + 2, "coordinate", processor.coordinate(), true);
                stringField(json, level + 2, "version", processor.version(), false);
                indent(json, level + 1).append("}");
                if (index < processors.size() - 1) {
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

    private static void pluginArray(
            StringBuilder json,
            int level,
            String name,
            List<MavenPluginInspection> plugins,
            boolean trailingComma) {
        indent(json, level).append('"').append(name).append("\": [");
        if (!plugins.isEmpty()) {
            json.append('\n');
            for (int index = 0; index < plugins.size(); index++) {
                MavenPluginInspection plugin = plugins.get(index);
                indent(json, level + 1).append("{\n");
                stringField(json, level + 2, "coordinate", plugin.coordinate(), true);
                stringArrayField(json, level + 2, "phases", plugin.phases(), true);
                booleanField(json, level + 2, "pluginManagement", plugin.pluginManagement(), false);
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

    private static void profileArray(
            StringBuilder json,
            int level,
            String name,
            List<MavenProfileInspection> profiles,
            boolean trailingComma) {
        indent(json, level).append('"').append(name).append("\": [");
        if (!profiles.isEmpty()) {
            json.append('\n');
            for (int index = 0; index < profiles.size(); index++) {
                MavenProfileInspection profile = profiles.get(index);
                indent(json, level + 1).append("{\n");
                stringField(json, level + 2, "id", profile.id(), true);
                stringArrayField(json, level + 2, "activationHints", profile.activationHints(), false);
                indent(json, level + 1).append("}");
                if (index < profiles.size() - 1) {
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
