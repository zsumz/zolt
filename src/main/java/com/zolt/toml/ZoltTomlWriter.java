package com.zolt.toml;

import com.zolt.project.BuildSettings;
import com.zolt.project.DependencySection;
import com.zolt.project.NativeSettings;
import com.zolt.project.ProjectConfig;
import com.zolt.project.ProjectMetadata;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

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
        writeStringMap(toml, "platforms", config.platforms());
        writeDependencies(toml, "dependencies", config.dependencies(), config.managedDependencies());
        writeDependencies(toml, "test.dependencies", config.testDependencies(), config.managedTestDependencies());
        writeBuild(toml, config.build());
        writeNative(toml, config.nativeSettings());
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
                    config.platforms(),
                    put(config.dependencies(), coordinate, version),
                    remove(config.managedDependencies(), coordinate),
                    config.testDependencies(),
                    config.managedTestDependencies(),
                    config.build(),
                    config.nativeSettings());
            case TEST -> new ProjectConfig(
                    config.project(),
                    config.repositories(),
                    config.platforms(),
                    config.dependencies(),
                    config.managedDependencies(),
                    put(config.testDependencies(), coordinate, version),
                    remove(config.managedTestDependencies(), coordinate),
                    config.build(),
                    config.nativeSettings());
        };
    }

    public ProjectConfig removeDependency(ProjectConfig config, DependencySection section, String coordinate) {
        return switch (section) {
            case MAIN -> new ProjectConfig(
                    config.project(),
                    config.repositories(),
                    config.platforms(),
                    remove(config.dependencies(), coordinate),
                    remove(config.managedDependencies(), coordinate),
                    config.testDependencies(),
                    config.managedTestDependencies(),
                    config.build(),
                    config.nativeSettings());
            case TEST -> new ProjectConfig(
                    config.project(),
                    config.repositories(),
                    config.platforms(),
                    config.dependencies(),
                    config.managedDependencies(),
                    remove(config.testDependencies(), coordinate),
                    remove(config.managedTestDependencies(), coordinate),
                    config.build(),
                    config.nativeSettings());
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

    private static void writeNative(StringBuilder toml, NativeSettings nativeSettings) {
        NativeSettings defaults = NativeSettings.defaults();
        if (nativeSettings == null
                || ((nativeSettings.imageName() == null || nativeSettings.imageName().isBlank())
                && nativeSettings.output().equals(defaults.output())
                && nativeSettings.args().isEmpty())) {
            return;
        }
        toml.append("\n[native]\n");
        if (nativeSettings.imageName() != null && !nativeSettings.imageName().isBlank()) {
            writeAssignment(toml, "imageName", nativeSettings.imageName());
        }
        writeAssignment(toml, "output", nativeSettings.output());
        writeStringArray(toml, "args", nativeSettings.args());
    }

    private static void writeStringMap(StringBuilder toml, String section, Map<String, String> values) {
        toml.append('[').append(section).append("]\n");
        for (Map.Entry<String, String> entry : sorted(values).entrySet()) {
            toml.append(quote(entry.getKey())).append(" = ").append(quote(entry.getValue())).append('\n');
        }
        toml.append('\n');
    }

    private static void writeDependencies(
            StringBuilder toml,
            String section,
            Map<String, String> versioned,
            Set<String> managed) {
        toml.append('[').append(section).append("]\n");
        for (String coordinate : sortedCoordinates(versioned, managed)) {
            toml.append(quote(coordinate)).append(" = ");
            String version = versioned.get(coordinate);
            if (version == null) {
                toml.append("{}");
            } else {
                toml.append(quote(version));
            }
            toml.append('\n');
        }
        toml.append('\n');
    }

    private static void writeAssignment(StringBuilder toml, String key, String value) {
        toml.append(key).append(" = ").append(quote(value)).append('\n');
    }

    private static void writeStringArray(StringBuilder toml, String key, List<String> values) {
        toml.append(key).append(" = [");
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) {
                toml.append(", ");
            }
            toml.append(quote(values.get(index)));
        }
        toml.append("]\n");
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

    private static Set<String> remove(Set<String> source, String coordinate) {
        TreeSet<String> updated = new TreeSet<>(source);
        updated.remove(coordinate);
        return updated;
    }

    private static Map<String, String> sorted(Map<String, String> values) {
        return new TreeMap<>(values);
    }

    private static Set<String> sortedCoordinates(Map<String, String> versioned, Set<String> managed) {
        TreeSet<String> coordinates = new TreeSet<>();
        coordinates.addAll(versioned.keySet());
        coordinates.addAll(managed);
        return coordinates;
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
