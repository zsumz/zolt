package sh.zolt.cli.command.dependency;

import sh.zolt.cli.CommandHumanOutput;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

final class DependencyEditCommentWarning {
    private DependencyEditCommentWarning() {
    }

    static void printIfNeeded(CommandHumanOutput output, Path configPath) {
        try {
            if (containsTomlComment(Files.readString(configPath))) {
                output.detail("Warning: zolt.toml contains comments; this edit rewrites the file and may remove comments or formatting.");
            }
        } catch (IOException ignored) {
            // The parser/write path reports real read/write failures. This guard is only advisory.
        }
    }

    static boolean containsTomlComment(String content) {
        Mode mode = Mode.NORMAL;
        for (int index = 0; index < content.length(); index++) {
            char character = content.charAt(index);
            switch (mode) {
                case NORMAL -> {
                    if (character == '#') {
                        return true;
                    }
                    if (startsWith(content, index, "\"\"\"")) {
                        mode = Mode.MULTILINE_BASIC;
                        index += 2;
                    } else if (startsWith(content, index, "'''")) {
                        mode = Mode.MULTILINE_LITERAL;
                        index += 2;
                    } else if (character == '"') {
                        mode = Mode.BASIC;
                    } else if (character == '\'') {
                        mode = Mode.LITERAL;
                    }
                }
                case BASIC -> {
                    if (character == '\\') {
                        index++;
                    } else if (character == '"') {
                        mode = Mode.NORMAL;
                    }
                }
                case LITERAL -> {
                    if (character == '\'') {
                        mode = Mode.NORMAL;
                    }
                }
                case MULTILINE_BASIC -> {
                    if (character == '\\') {
                        index++;
                    } else if (startsWith(content, index, "\"\"\"")) {
                        mode = Mode.NORMAL;
                        index += 2;
                    }
                }
                case MULTILINE_LITERAL -> {
                    if (startsWith(content, index, "'''")) {
                        mode = Mode.NORMAL;
                        index += 2;
                    }
                }
            }
        }
        return false;
    }

    private static boolean startsWith(String content, int index, String value) {
        return index + value.length() <= content.length()
                && content.regionMatches(index, value, 0, value.length());
    }

    private enum Mode {
        NORMAL,
        BASIC,
        LITERAL,
        MULTILINE_BASIC,
        MULTILINE_LITERAL
    }
}
