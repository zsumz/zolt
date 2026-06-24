package com.zolt.cli.command;

import com.zolt.cli.CommandHumanOutput;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import picocli.CommandLine;
import picocli.CommandLine.Model.CommandSpec;

final class CommandFailures {
    private CommandFailures() {
    }

    static CommandLine.ExecutionException user(CommandSpec spec, Exception exception) {
        return user(spec, exception.getMessage(), exception);
    }

    static CommandLine.ExecutionException user(CommandSpec spec, String displayMessage, Exception exception) {
        printUser(spec, displayMessage);
        return new CommandLine.ExecutionException(spec.commandLine(), exception.getMessage(), exception);
    }

    static void printUser(CommandSpec spec, Exception exception) {
        printUser(spec, exception.getMessage());
    }

    static void printUser(CommandSpec spec, String displayMessage) {
        ErrorBlock block = ErrorBlock.from(displayMessage);
        CommandHumanOutput output = CommandHumanOutput.errors(spec);
        output.error(block.summary());
        if (!block.contextRows().isEmpty() || block.next().isPresent()) {
            output.blankLine();
        }
        for (ContextRow row : block.contextRows()) {
            output.context(row.label(), row.value());
        }
        block.next().ifPresent(output::next);
        spec.commandLine().getErr().flush();
    }

    private record ErrorBlock(String summary, List<ContextRow> contextRows, Optional<String> next) {
        private static final Pattern COORDINATE =
                Pattern.compile("([A-Za-z0-9_.-]+:[A-Za-z0-9_.-]+(?::[A-Za-z0-9_.-]+)?)");

        static ErrorBlock from(String displayMessage) {
            String message = displayMessage == null || displayMessage.isBlank()
                    ? "Command failed."
                    : displayMessage.trim();
            SplitMessage split = split(message);
            return new ErrorBlock(split.summary(), contextRows(split.summary()), split.next());
        }

        private static SplitMessage split(String message) {
            int index = remediationIndex(message);
            if (index < 0) {
                return new SplitMessage(message, Optional.empty());
            }
            String summary = message.substring(0, index).trim();
            String next = message.substring(index).trim();
            if (summary.endsWith(".")) {
                summary = summary.substring(0, summary.length() - 1);
            }
            return new SplitMessage(summary + ".", Optional.of(next));
        }

        private static int remediationIndex(String message) {
            List<String> starters = List.of(
                    " Check that ",
                    " Run `",
                    " Run the ",
                    " Use ",
                    " Remove ",
                    " Fix ",
                    " Create ",
                    " Move ",
                    " Add ",
                    " Edit ",
                    " Set ",
                    " Install ",
                    " Delete ");
            int best = -1;
            for (String starter : starters) {
                int found = message.indexOf(starter);
                if (found >= 0 && (best < 0 || found < best)) {
                    best = found;
                }
            }
            return best;
        }

        private static List<ContextRow> contextRows(String summary) {
            List<ContextRow> rows = new ArrayList<>();
            pathAfter(summary, "zolt.toml at ").ifPresent(path -> rows.add(new ContextRow("File", path)));
            pathAfter(summary, "zolt.lock at ").ifPresent(path -> rows.add(new ContextRow("File", path)));
            if (rows.stream().noneMatch(row -> "File".equals(row.label()))) {
                if (summary.contains("zolt.toml")) {
                    rows.add(new ContextRow("File", "zolt.toml"));
                } else if (summary.contains("zolt.lock")) {
                    rows.add(new ContextRow("File", "zolt.lock"));
                }
            }
            bracketAfter(summary, "Unknown field ").ifPresent(field -> rows.add(new ContextRow("Field", field)));
            bracketAfter(summary, "Unknown top-level section ").ifPresent(section -> rows.add(new ContextRow("Section", section)));
            backtickAfter(summary, "Unsupported ").ifPresent(value -> rows.add(new ContextRow("Unsupported", value)));
            coordinate(summary).ifPresent(coordinate -> rows.add(new ContextRow("Coordinate", coordinate)));
            return rows;
        }

        private static Optional<String> pathAfter(String text, String marker) {
            int start = text.indexOf(marker);
            if (start < 0) {
                return Optional.empty();
            }
            start += marker.length();
            int end = text.indexOf(". ", start);
            if (end < 0) {
                end = text.length();
            }
            String path = stripTrailingPunctuation(text.substring(start, end).trim());
            return path.isBlank() ? Optional.empty() : Optional.of(path);
        }

        private static Optional<String> bracketAfter(String text, String marker) {
            int start = text.indexOf(marker);
            if (start < 0) {
                return Optional.empty();
            }
            start = text.indexOf('[', start);
            int end = text.indexOf(']', start);
            if (start < 0 || end < 0) {
                return Optional.empty();
            }
            while (end + 1 < text.length() && !Character.isWhitespace(text.charAt(end + 1))) {
                end++;
            }
            return Optional.of(stripTrailingPunctuation(text.substring(start, end + 1)));
        }

        private static Optional<String> backtickAfter(String text, String marker) {
            int start = text.indexOf(marker);
            if (start < 0) {
                return Optional.empty();
            }
            start = text.indexOf('`', start);
            int end = text.indexOf('`', start + 1);
            if (start < 0 || end < 0) {
                return Optional.empty();
            }
            return Optional.of(text.substring(start, end + 1));
        }

        private static Optional<String> coordinate(String text) {
            Matcher matcher = COORDINATE.matcher(text);
            return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
        }

        private static String stripTrailingPunctuation(String value) {
            while (value.endsWith(".") || value.endsWith(",")) {
                value = value.substring(0, value.length() - 1).trim();
            }
            return value;
        }
    }

    private record SplitMessage(String summary, Optional<String> next) {
    }

    private record ContextRow(String label, String value) {
    }
}
