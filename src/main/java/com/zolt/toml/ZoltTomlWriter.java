package com.zolt.toml;

import com.zolt.project.BuildSettings;
import com.zolt.project.DependencySection;
import com.zolt.project.ProjectConfig;
import com.zolt.project.ProjectMetadata;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

public final class ZoltTomlWriter {
    public ProjectConfig defaultApplicationConfig(String name, String group, String mainClass) {
        return new ProjectConfig(
                new ProjectMetadata(name, "0.1.0", group, "21", Optional.ofNullable(blankToNull(mainClass))),
                ProjectConfig.defaultRepositories(),
                Map.of(),
                Map.of(),
                BuildSettings.defaults());
    }

    public void write(Path path, ProjectConfig config) {
        try {
            Files.writeString(path, write(config));
        } catch (IOException exception) {
            throw new ZoltConfigException(
                    "Could not write zolt.toml at " + path + ". Check that the directory exists and is writable.");
        }
    }

    public String write(ProjectConfig config) {
        StringBuilder toml = new StringBuilder();
        writeProject(toml, config.project());
        writeStringMap(toml, "repositories", config.repositories());
        writeStringMap(toml, "dependencies", config.dependencies());
        writeStringMap(toml, "test.dependencies", config.testDependencies());
        writeBuild(toml, config.build());
        return toml.toString();
    }

    public ProjectConfig addDependency(
            ProjectConfig config,
            DependencySection section,
            String coordinate,
            String version) {
        return switch (section) {
            case MAIN -> new ProjectConfig(
                    config.project(),
                    config.repositories(),
                    put(config.dependencies(), coordinate, version),
                    config.testDependencies(),
                    config.build());
            case TEST -> new ProjectConfig(
                    config.project(),
                    config.repositories(),
                    config.dependencies(),
                    put(config.testDependencies(), coordinate, version),
                    config.build());
        };
    }

    public ProjectConfig removeDependency(ProjectConfig config, DependencySection section, String coordinate) {
        return switch (section) {
            case MAIN -> new ProjectConfig(
                    config.project(),
                    config.repositories(),
                    remove(config.dependencies(), coordinate),
                    config.testDependencies(),
                    config.build());
            case TEST -> new ProjectConfig(
                    config.project(),
                    config.repositories(),
                    config.dependencies(),
                    remove(config.testDependencies(), coordinate),
                    config.build());
        };
    }

    private static void writeProject(StringBuilder toml, ProjectMetadata project) {
        toml.append("[project]\n");
        writeAssignment(toml, "name", project.name());
        writeAssignment(toml, "version", project.version());
        writeAssignment(toml, "group", project.group());
        writeAssignment(toml, "java", project.java());
        project.main().ifPresent(mainClass -> writeAssignment(toml, "main", mainClass));
        toml.append('\n');
    }

    private static void writeBuild(StringBuilder toml, BuildSettings build) {
        toml.append("[build]\n");
        writeAssignment(toml, "source", build.source());
        writeAssignment(toml, "test", build.test());
        writeAssignment(toml, "output", build.output());
        writeAssignment(toml, "testOutput", build.testOutput());
    }

    private static void writeStringMap(StringBuilder toml, String section, Map<String, String> values) {
        toml.append('[').append(section).append("]\n");
        for (Map.Entry<String, String> entry : sorted(values).entrySet()) {
            toml.append(quote(entry.getKey())).append(" = ").append(quote(entry.getValue())).append('\n');
        }
        toml.append('\n');
    }

    private static void writeAssignment(StringBuilder toml, String key, String value) {
        toml.append(key).append(" = ").append(quote(value)).append('\n');
    }

    private static Map<String, String> put(Map<String, String> source, String coordinate, String version) {
        Map<String, String> updated = new LinkedHashMap<>(source);
        updated.put(coordinate, version);
        return updated;
    }

    private static Map<String, String> remove(Map<String, String> source, String coordinate) {
        Map<String, String> updated = new LinkedHashMap<>(source);
        updated.remove(coordinate);
        return updated;
    }

    private static Map<String, String> sorted(Map<String, String> values) {
        return new TreeMap<>(values);
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
