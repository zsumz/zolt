package com.zolt.cli;

import com.zolt.cli.console.ConsoleStyle;
import java.io.PrintWriter;
import picocli.CommandLine;
import picocli.CommandLine.Model.CommandSpec;

public final class CommandHumanOutput {
    private final PrintWriter out;
    private final ConsoleStyle style;

    private CommandHumanOutput(PrintWriter out, ConsoleStyle style) {
        this.out = out;
        this.style = style;
    }

    public static CommandHumanOutput of(CommandSpec spec) {
        CommandLine commandLine = spec.commandLine();
        ZoltCli root = root(commandLine);
        ConsoleStyle style = root == null ? ConsoleStyle.disabled() : root.consoleStyle();
        return new CommandHumanOutput(commandLine.getOut(), style);
    }

    public void work(String message) {
        out.println(styledLead(message, LeadStyle.WORK));
    }

    public void success(String message) {
        out.println(styledLead(message, LeadStyle.SUCCESS));
    }

    public void detail(String message) {
        out.println(styledLead(message, LeadStyle.DETAIL));
    }

    public void action(String command) {
        out.println("Next: " + style.command(command));
    }

    public void line(String message) {
        out.println(message);
    }

    public void blankLine() {
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
                case "Wrote", "Verified", "Included", "Copied", "Generated", "Ran", "Compiled" -> style.success(text);
                default -> text;
            };
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
