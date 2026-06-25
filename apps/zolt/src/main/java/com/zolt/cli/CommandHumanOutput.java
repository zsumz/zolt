package com.zolt.cli;

import com.zolt.cli.console.ConsoleStyle;
import java.io.PrintWriter;
import picocli.CommandLine;
import picocli.CommandLine.Model.CommandSpec;

public final class CommandHumanOutput {
    private final PrintWriter out;
    private final ConsoleStyle style;
    private final boolean quiet;

    private CommandHumanOutput(PrintWriter out, ConsoleStyle style, boolean quiet) {
        this.out = out;
        this.style = style;
        this.quiet = quiet;
    }

    public static CommandHumanOutput of(CommandSpec spec) {
        CommandLine commandLine = spec.commandLine();
        ZoltCli root = root(commandLine);
        ConsoleStyle style = root == null ? ConsoleStyle.disabled() : root.consoleStyle();
        boolean quiet = root != null && root.quiet();
        return new CommandHumanOutput(commandLine.getOut(), style, quiet);
    }

    public static CommandHumanOutput errors(CommandSpec spec) {
        CommandLine commandLine = spec.commandLine();
        ZoltCli root = root(commandLine);
        ConsoleStyle style = root == null ? ConsoleStyle.disabled() : root.consoleStyle();
        return new CommandHumanOutput(commandLine.getErr(), style, false);
    }

    public void work(String message) {
        if (quiet) {
            return;
        }
        out.println(styledLead(message, LeadStyle.WORK));
    }

    public void success(String message) {
        if (quiet) {
            return;
        }
        out.println(styledLead(message, LeadStyle.SUCCESS));
    }

    public void detail(String message) {
        if (quiet) {
            return;
        }
        out.println(styledLead(message, LeadStyle.DETAIL));
    }

    public void action(String command) {
        if (quiet) {
            return;
        }
        out.println("Next: " + style.command(command));
    }

    public void check(String marker, String message) {
        if (quiet) {
            return;
        }
        out.println(styleStatus(marker) + " " + message);
    }

    public void error(String message) {
        out.println(style.error("error:") + " " + message);
    }

    public void context(String label, String value) {
        if (quiet) {
            return;
        }
        out.println(label + ": " + value);
    }

    public void status(String label, String marker) {
        if (quiet) {
            return;
        }
        out.println(label + ": " + styleStatus(marker));
    }

    public void next(String message) {
        if (quiet) {
            return;
        }
        out.println("Next: " + message);
    }

    public void line(String message) {
        if (quiet) {
            return;
        }
        out.println(message);
    }

    public void blankLine() {
        if (quiet) {
            return;
        }
        out.println();
    }

    private String styledLead(String message, LeadStyle leadStyle) {
        int separator = message.indexOf(' ');
        if (separator < 0) {
            return style(leadStyle, message);
        }
        return style(leadStyle, message.substring(0, separator)) + message.substring(separator);
    }

    private String style(LeadStyle leadStyle, String text) {
        return switch (leadStyle) {
            case WORK -> style.work(text);
            case SUCCESS -> style.success(text);
            case DETAIL -> switch (text) {
                case "Wrote", "Verified", "Included", "Copied", "Generated", "Ran", "Compiled", "Unpacked",
                        "Downloaded", "Skipped", "Preserved" -> style.success(text);
                default -> text;
            };
        };
    }

    private String styleStatus(String marker) {
        return switch (marker) {
            case "ok" -> style.success(marker);
            case "warning" -> style.warning(marker);
            case "error" -> style.error(marker);
            case "skip" -> style.muted(marker);
            default -> marker;
        };
    }

    private static ZoltCli root(CommandLine commandLine) {
        CommandLine current = commandLine;
        while (current.getParent() != null) {
            current = current.getParent();
        }
        Object userObject = current.getCommandSpec().userObject();
        return userObject instanceof ZoltCli root ? root : null;
    }

    private enum LeadStyle {
        WORK,
        SUCCESS,
        DETAIL
    }
}
