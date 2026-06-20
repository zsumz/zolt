package com.zolt.ide;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

final class IdeJsonFields {
    private IdeJsonFields() {
    }

    static void stringField(StringBuilder json, int level, String name, String value, boolean trailingComma) {
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

    static void pathField(StringBuilder json, int level, String name, Path value, boolean trailingComma) {
        stringField(json, level, name, value == null ? null : jsonPath(value), trailingComma);
    }

    static void field(StringBuilder json, int level, String name, int value, boolean trailingComma) {
        indent(json, level).append('"').append(name).append("\": ").append(value);
        if (trailingComma) {
            json.append(',');
        }
        json.append('\n');
    }

    static void field(StringBuilder json, int level, String name, boolean value, boolean trailingComma) {
        indent(json, level).append('"').append(name).append("\": ").append(value);
        if (trailingComma) {
            json.append(',');
        }
        json.append('\n');
    }

    static void pathArrayField(
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

    static void stringArrayField(
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

    static void stringMap(
            StringBuilder json,
            int level,
            String name,
            Map<String, String> values,
            boolean trailingComma) {
        indent(json, level).append('"').append(name).append("\": {");
        if (!values.isEmpty()) {
            json.append('\n');
            int index = 0;
            Map<String, String> sorted = new TreeMap<>(values);
            for (Map.Entry<String, String> entry : sorted.entrySet()) {
                indent(json, level + 1);
                string(json, entry.getKey());
                json.append(": ");
                string(json, entry.getValue());
                if (index < sorted.size() - 1) {
                    json.append(',');
                }
                json.append('\n');
                index++;
            }
            indent(json, level);
        }
        json.append("}");
        if (trailingComma) {
            json.append(',');
        }
        json.append('\n');
    }

    static void string(StringBuilder json, String value) {
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

    static StringBuilder indent(StringBuilder json, int level) {
        return json.append("  ".repeat(level));
    }

    static void comma(StringBuilder json) {
        json.append(",\n");
    }

    private static String jsonPath(Path path) {
        return path.toString().replace('\\', '/');
    }
}
