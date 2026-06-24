package com.zolt.cli;

import com.zolt.cli.console.ConsoleStyle;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class HelpSynopsisHighlighter {
    private static final Pattern OPTION_TOKEN = Pattern.compile("-{1,2}[A-Za-z][A-Za-z0-9-]*");
    private static final Pattern COMMAND_WORD = Pattern.compile("[a-z][a-z0-9-]*");

    private HelpSynopsisHighlighter() {
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
        int commandEnd = commandEnd(line);
        if (commandEnd < 0) {
            return highlightMetaTokens(line, style);
        }

        StringBuilder highlighted = new StringBuilder(line.length());
        int commandStart = firstNonSpace(line);
        highlighted.append(line, 0, commandStart);
        highlighted.append(style.helpCommand(line.substring(commandStart, commandEnd)));
        highlighted.append(highlightMetaTokens(line.substring(commandEnd), style));
        return highlighted.toString();
    }

    private static int commandEnd(String line) {
        int index = firstNonSpace(line);
        if (!line.startsWith("zolt", index)) {
            return -1;
        }

        int end = index + "zolt".length();
        while (end < line.length()) {
            int spaceStart = end;
            while (end < line.length() && line.charAt(end) == ' ') {
                end++;
            }
            if (spaceStart == end || end >= line.length()) {
                return spaceStart;
            }
            Matcher commandWord = COMMAND_WORD.matcher(line);
            commandWord.region(end, line.length());
            if (!commandWord.lookingAt()) {
                return spaceStart;
            }
            end = commandWord.end();
        }
        return end;
    }

    private static int firstNonSpace(String line) {
        int index = 0;
        while (index < line.length() && line.charAt(index) == ' ') {
            index++;
        }
        return index;
    }

    private static String highlightMetaTokens(String text, ConsoleStyle style) {
        StringBuilder highlighted = new StringBuilder(text.length());
        int index = 0;
        while (index < text.length()) {
            char current = text.charAt(index);
            if (Character.isWhitespace(current)) {
                highlighted.append(current);
                index++;
                continue;
            }

            int end = index;
            while (end < text.length() && !Character.isWhitespace(text.charAt(end))) {
                end++;
            }
            highlighted.append(highlightMetaToken(text.substring(index, end), style));
            index = end;
        }
        return highlighted.toString();
    }

    private static String highlightMetaToken(String token, ConsoleStyle style) {
        Matcher matcher = OPTION_TOKEN.matcher(token);
        StringBuilder highlighted = new StringBuilder(token.length());
        int previous = 0;
        while (matcher.find()) {
            if (matcher.start() > previous) {
                highlighted.append(style.helpMeta(token.substring(previous, matcher.start())));
            }
            highlighted.append(style.option(matcher.group()));
            previous = matcher.end();
        }
        if (previous < token.length()) {
            highlighted.append(style.helpMeta(token.substring(previous)));
        }
        return highlighted.toString();
    }
}
