package com.zolt.build;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

final class PackageEvidenceJsonFields {
    private PackageEvidenceJsonFields() {
    }

    static void nullablePathField(
            StringBuilder json,
            int level,
            String name,
            Path projectRoot,
            Optional<Path> value,
            boolean trailingComma) {
        nullableStringField(json, level, name, value.map(path -> displayPath(projectRoot, path)), trailingComma);
    }

    static void nullableStringField(
            StringBuilder json,
            int level,
            String name,
            Optional<String> value,
            boolean trailingComma) {
        indent(json, level);
        string(json, name);
        json.append(": ");
        if (value.isPresent()) {
            string(json, value.orElseThrow());
        } else {
            json.append("null");
        }
        if (trailingComma) {
            json.append(',');
        }
        json.append('\n');
    }

    static void stringArrayField(
            StringBuilder json,
            int level,
            String name,
            List<String> values,
            boolean trailingComma) {
        indent(json, level);
        string(json, name);
        json.append(": [");
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) {
                json.append(", ");
            }
            string(json, values.get(index));
        }
        json.append("]");
        if (trailingComma) {
            json.append(',');
        }
        json.append('\n');
    }

    static void stringField(
            StringBuilder json,
            int level,
            String name,
            String value,
            boolean trailingComma) {
        indent(json, level);
        string(json, name);
        json.append(": ");
        string(json, value);
        if (trailingComma) {
            json.append(',');
        }
        json.append('\n');
    }

    static void booleanField(
            StringBuilder json,
            int level,
            String name,
            boolean value,
            boolean trailingComma) {
        indent(json, level);
        string(json, name);
        json.append(": ").append(value);
        if (trailingComma) {
            json.append(',');
        }
        json.append('\n');
    }

    static void intField(
            StringBuilder json,
            int level,
            String name,
            int value,
            boolean trailingComma) {
        indent(json, level);
        string(json, name);
        json.append(": ").append(value);
        if (trailingComma) {
            json.append(',');
        }
        json.append('\n');
    }

    static String displayPath(Path projectRoot, Path path) {
        Path normalized = path.toAbsolutePath().normalize();
        if (normalized.startsWith(projectRoot)) {
            return projectRoot.relativize(normalized).toString().replace('\\', '/');
        }
        return normalized.toString();
    }

    static StringBuilder indent(StringBuilder json, int level) {
        return json.append("  ".repeat(level));
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
}
