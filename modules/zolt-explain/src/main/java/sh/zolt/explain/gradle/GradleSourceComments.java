package sh.zolt.explain.gradle;

final class GradleSourceComments {
    private GradleSourceComments() {}

    static String stripComments(String content) {
        StringBuilder stripped = new StringBuilder(content.length());
        State state = State.CODE;
        char quote = '\0';
        int index = 0;
        while (index < content.length()) {
            char character = content.charAt(index);
            switch (state) {
                case CODE -> {
                    if (startsWith(content, index, "/*")) {
                        trimCommentOnlyLineWhitespace(stripped);
                        index = skipBlockComment(content, index + 2, stripped);
                    } else if (startsWith(content, index, "//")) {
                        trimTrailingHorizontalWhitespace(stripped);
                        index = skipLineComment(content, index + 2, stripped);
                    } else if (character == '\'' || character == '"') {
                        quote = character;
                        if (startsWithTripleQuote(content, index, character)) {
                            stripped.append(character).append(character).append(character);
                            index += 3;
                            state = State.TRIPLE_STRING;
                        } else {
                            stripped.append(character);
                            index++;
                            state = State.STRING;
                        }
                    } else {
                        stripped.append(character);
                        index++;
                    }
                }
                case STRING -> {
                    stripped.append(character);
                    if (character == '\\') {
                        index++;
                        if (index < content.length()) {
                            stripped.append(content.charAt(index));
                            index++;
                        }
                    } else {
                        index++;
                        if (character == quote || character == '\n' || character == '\r') {
                            state = State.CODE;
                        }
                    }
                }
                case TRIPLE_STRING -> {
                    if (startsWithTripleQuote(content, index, quote)) {
                        stripped.append(quote).append(quote).append(quote);
                        index += 3;
                        state = State.CODE;
                    } else {
                        stripped.append(character);
                        index++;
                    }
                }
            }
        }
        return stripped.toString();
    }

    private static int skipBlockComment(String content, int index, StringBuilder stripped) {
        while (index < content.length()) {
            if (startsWith(content, index, "*/")) {
                return index + 2;
            }
            char character = content.charAt(index);
            if (character == '\n' || character == '\r') {
                stripped.append(character);
            }
            index++;
        }
        return index;
    }

    private static int skipLineComment(String content, int index, StringBuilder stripped) {
        while (index < content.length()) {
            char character = content.charAt(index);
            if (character == '\n' || character == '\r') {
                stripped.append(character);
                return index + 1;
            }
            index++;
        }
        return index;
    }

    private static void trimCommentOnlyLineWhitespace(StringBuilder stripped) {
        int index = stripped.length() - 1;
        while (index >= 0) {
            char character = stripped.charAt(index);
            if (character == '\n' || character == '\r') {
                trimTrailingHorizontalWhitespace(stripped);
                return;
            }
            if (character != ' ' && character != '\t') {
                return;
            }
            index--;
        }
        trimTrailingHorizontalWhitespace(stripped);
    }

    private static void trimTrailingHorizontalWhitespace(StringBuilder stripped) {
        while (!stripped.isEmpty()) {
            char character = stripped.charAt(stripped.length() - 1);
            if (character != ' ' && character != '\t') {
                return;
            }
            stripped.deleteCharAt(stripped.length() - 1);
        }
    }

    private static boolean startsWithTripleQuote(String content, int index, char quote) {
        return index + 2 < content.length()
                && content.charAt(index) == quote
                && content.charAt(index + 1) == quote
                && content.charAt(index + 2) == quote;
    }

    private static boolean startsWith(String content, int index, String value) {
        return index + value.length() <= content.length() && content.startsWith(value, index);
    }

    private enum State {
        CODE,
        STRING,
        TRIPLE_STRING
    }
}
