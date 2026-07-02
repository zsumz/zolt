package sh.zolt.cli;

import sh.zolt.cli.console.ConsoleStyle;
import java.util.regex.Pattern;

final class HelpParameterHighlighter {
    private static final String PARAMETER_WORD = "[A-Z][A-Z0-9_-]*(\\.\\.\\.)?";
    private static final Pattern PARAMETER_LABEL =
            Pattern.compile("(\\[[^\\s]+\\]|<[^\\s]+>|"
                    + PARAMETER_WORD
                    + "((:"
                    + PARAMETER_WORD
                    + ")|(\\[:"
                    + PARAMETER_WORD
                    + "\\]))*"
                    + ")");

    private HelpParameterHighlighter() {
    }

    static String highlight(String text, ConsoleStyle style) {
        if (!style.enabledColor() || text.isEmpty()) {
            return text;
        }

        String[] lines = text.split("\n", -1);
        StringBuilder highlighted = new StringBuilder(text.length());
        for (int index = 0; index < lines.length; index++) {
            if (index > 0) {
                highlighted.append('\n');
            }
            highlighted.append(highlightLine(lines[index], style));
        }
        return highlighted.toString();
    }

    private static String highlightLine(String line, ConsoleStyle style) {
        int tokenStart = firstNonSpace(line);
        if (tokenStart >= line.length()) {
            return line;
        }
        int tokenEnd = tokenEnd(line, tokenStart);
        if (!PARAMETER_LABEL.matcher(line.substring(tokenStart, tokenEnd)).matches()) {
            return line;
        }
        return line.substring(0, tokenStart)
                + style.helpMeta(line.substring(tokenStart, tokenEnd))
                + line.substring(tokenEnd);
    }

    private static int firstNonSpace(String line) {
        int index = 0;
        while (index < line.length() && line.charAt(index) == ' ') {
            index++;
        }
        return index;
    }

    private static int tokenEnd(String line, int tokenStart) {
        int index = tokenStart;
        while (index < line.length() && !Character.isWhitespace(line.charAt(index))) {
            index++;
        }
        return index;
    }
}
