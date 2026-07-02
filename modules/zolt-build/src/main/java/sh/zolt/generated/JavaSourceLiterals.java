package sh.zolt.generated;

final class JavaSourceLiterals {
    private JavaSourceLiterals() {
    }

    static String string(String value) {
        StringBuilder literal = new StringBuilder("\"");
        for (int index = 0; index < value.length(); index++) {
            appendEscaped(literal, value.charAt(index));
        }
        return literal.append('"').toString();
    }

    private static void appendEscaped(StringBuilder literal, char character) {
        switch (character) {
            case '\\' -> literal.append("\\\\");
            case '"' -> literal.append("\\\"");
            case '\b' -> literal.append("\\b");
            case '\f' -> literal.append("\\f");
            case '\n' -> literal.append("\\n");
            case '\r' -> literal.append("\\r");
            case '\t' -> literal.append("\\t");
            default -> appendDefault(literal, character);
        }
    }

    private static void appendDefault(StringBuilder literal, char character) {
        if (Character.isISOControl(character)) {
            literal.append("\\u").append(String.format("%04x", (int) character));
        } else {
            literal.append(character);
        }
    }
}
