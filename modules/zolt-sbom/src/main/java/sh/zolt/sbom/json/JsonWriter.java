package sh.zolt.sbom.json;

/**
 * Minimal, deterministic JSON emitter shared by the SBOM writers.
 *
 * <p>This is a copy of the {@code zolt-tree} {@code DependencyJsonFields} pattern (which is
 * package-private there): a {@link StringBuilder} plus indentation-aware field helpers, hand-rolled
 * so every byte is under our control. No external JSON library reaches the SBOM output, and no
 * hash-ordered iteration is possible because callers pass already-sorted collections.
 */
public final class JsonWriter {
    private JsonWriter() {
    }

    /** Two spaces per level, matching the {@code zolt-tree} JSON layout. */
    public static StringBuilder indent(StringBuilder json, int level) {
        return json.append("  ".repeat(level));
    }

    public static void stringField(StringBuilder json, int level, String name, String value, boolean trailingComma) {
        indent(json, level);
        string(json, name);
        json.append(": ");
        string(json, value);
        terminate(json, trailingComma);
    }

    /**
     * Emits {@code "name": <raw>} where {@code raw} is already a valid JSON token (a number or a
     * literal). Used for the integer {@code version} field.
     */
    public static void rawField(StringBuilder json, int level, String name, String raw, boolean trailingComma) {
        indent(json, level);
        string(json, name);
        json.append(": ").append(raw);
        terminate(json, trailingComma);
    }

    public static void openObject(StringBuilder json, int level, String name) {
        indent(json, level);
        string(json, name);
        json.append(": {\n");
    }

    public static void openArray(StringBuilder json, int level, String name) {
        indent(json, level);
        string(json, name);
        json.append(": [");
    }

    /** Writes a JSON string literal with the standard escapes and control-character fallback. */
    public static void string(StringBuilder json, String value) {
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

    private static void terminate(StringBuilder json, boolean trailingComma) {
        if (trailingComma) {
            json.append(',');
        }
        json.append('\n');
    }
}
