package com.zolt.workspace;

import com.zolt.toml.ZoltConfigException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public final class WorkspaceTomlWriter {
    public void write(Path path, WorkspaceConfig config) {
        try {
            Files.writeString(path, write(config));
        } catch (IOException exception) {
            throw new ZoltConfigException(
                    "Could not write zolt.toml at " + path + ". Check that the directory exists and is writable.");
        }
    }

    public String write(WorkspaceConfig config) {
        StringBuilder toml = new StringBuilder();
        toml.append("[workspace]\n");
        toml.append("name = \"").append(escape(config.name())).append("\"\n");
        toml.append("members = ").append(stringArray(config.members())).append("\n");
        if (!config.defaultMembers().isEmpty()) {
            toml.append("defaultMembers = ").append(stringArray(config.defaultMembers())).append("\n");
        }
        writeStringMap(toml, "repositories", config.repositories());
        writeStringMap(toml, "platforms", config.platforms());
        return toml.toString();
    }

    private static void writeStringMap(StringBuilder toml, String section, Map<String, String> values) {
        if (values.isEmpty()) {
            return;
        }
        toml.append("\n[").append(section).append("]\n");
        values.forEach((key, value) -> toml
                .append('"')
                .append(escape(key))
                .append("\" = \"")
                .append(escape(value))
                .append("\"\n"));
    }

    private static String stringArray(List<String> values) {
        StringBuilder array = new StringBuilder("[");
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) {
                array.append(", ");
            }
            array.append('"').append(escape(values.get(index))).append('"');
        }
        return array.append(']').toString();
    }

    private static String escape(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
