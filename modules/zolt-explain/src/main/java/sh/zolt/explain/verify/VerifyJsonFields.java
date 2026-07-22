package sh.zolt.explain.verify;

/**
 * Minimal hand-rolled JSON emit helpers, matching the style used elsewhere in the explain module
 * (two-space indent, deterministic field order, no external JSON dependency).
 */
final class VerifyJsonFields {
    private VerifyJsonFields() {
    }

    static void stringField(StringBuilder json, int level, String name, String value, boolean trailingComma) {
        indent(json, level).append('"').append(name).append("\": ");
        string(json, value);
        finishLine(json, trailingComma);
    }

    static void intField(StringBuilder json, int level, String name, int value, boolean trailingComma) {
        indent(json, level).append('"').append(name).append("\": ").append(value);
        finishLine(json, trailingComma);
    }

    static void nullableStringField(
            StringBuilder json, int level, String name, String value, boolean trailingComma) {
        indent(json, level).append('"').append(name).append("\": ");
        if (value == null) {
            json.append("null");
        } else {
            string(json, value);
        }
        finishLine(json, trailingComma);
    }

    static StringBuilder indent(StringBuilder json, int level) {
        return json.append("  ".repeat(level));
    }

    static void finishLine(StringBuilder json, boolean trailingComma) {
        if (trailingComma) {
            json.append(',');
        }
        json.append('\n');
    }

    static void string(StringBuilder json, String value) {
        json.append('"');
        String safe = value == null ? "" : value;
        for (int index = 0; index < safe.length(); index++) {
            char character = safe.charAt(index);
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
