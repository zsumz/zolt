package com.zolt.cli;

import com.zolt.cli.console.ConsoleStyle;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class HelpOptionHighlighter {
    private static final Pattern OPTION_LINE = Pattern.compile("^\\s+-");
    private static final Pattern OPTION_TOKEN = Pattern.compile("(?<!\\S)-{1,2}[A-Za-z][A-Za-z0-9-]*");

    private HelpOptionHighlighter() {
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
        if (!OPTION_LINE.matcher(line).find()) {
            return line;
        }

        Matcher matcher = OPTION_TOKEN.matcher(line);
        StringBuilder highlighted = new StringBuilder(line.length());
        int previous = 0;
        while (matcher.find()) {
            highlighted.append(line, previous, matcher.start());
            highlighted.append(style.option(matcher.group()));
            int metaEnd = metaEnd(line, matcher.end());
            if (metaEnd > matcher.end()) {
                highlighted.append(style.helpMeta(line.substring(matcher.end(), metaEnd)));
            }
            previous = metaEnd;
        }
        highlighted.append(line, previous, line.length());
        return highlighted.toString();
    }

    private static int metaEnd(String line, int index) {
        if (index >= line.length()) {
            return index;
        }
        char next = line.charAt(index);
        if (next == '=' || next == '[') {
            int end = index + 1;
            while (end < line.length() && !Character.isWhitespace(line.charAt(end)) && line.charAt(end) != ',') {
                end++;
            }
            return end;
        }
        if (next == ' ' && index + 1 < line.length() && isMetaStart(line.charAt(index + 1))) {
            int end = index + 2;
            while (end < line.length() && !Character.isWhitespace(line.charAt(end)) && line.charAt(end) != ',') {
                end++;
            }
            return end;
        }
        if (next == '.' && line.startsWith("...", index)) {
            return index + 3;
        }
        return index;
    }

    private static boolean isMetaStart(char current) {
        return current == '<' || current == '[';
    }
}
