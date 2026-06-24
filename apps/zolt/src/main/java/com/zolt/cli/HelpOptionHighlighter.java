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
        while (matcher.find()) {
            matcher.appendReplacement(highlighted, Matcher.quoteReplacement(style.option(matcher.group())));
        }
        matcher.appendTail(highlighted);
        return highlighted.toString();
    }
}
