package com.zolt.cli;

import com.zolt.cli.console.ConsoleStyle;
import com.zolt.provenance.BuildProvenance;
import com.zolt.provenance.GitProvenance;
import java.io.PrintWriter;
import java.util.Optional;
import picocli.CommandLine;
import picocli.CommandLine.Model.CommandSpec;

public final class CommandHumanOutput {
    private final PrintWriter out;
    private final ConsoleStyle style;
    private final boolean quiet;
    private final boolean verbose;

    private CommandHumanOutput(PrintWriter out, ConsoleStyle style, boolean quiet, boolean verbose) {
        this.out = out;
        this.style = style;
        this.quiet = quiet;
        this.verbose = verbose && !quiet;
    }

    public static CommandHumanOutput of(CommandSpec spec) {
        CommandLine commandLine = spec.commandLine();
        ZoltCli root = root(commandLine);
        ConsoleStyle style = root == null ? ConsoleStyle.disabled() : root.consoleStyle();
        boolean quiet = root != null && root.quiet();
        boolean verbose = root != null && root.verbose();
        return new CommandHumanOutput(commandLine.getOut(), style, quiet, verbose);
    }

    static CommandHumanOutput forTesting(PrintWriter out, ConsoleStyle style, boolean quiet) {
        return forTesting(out, style, quiet, false);
    }

    static CommandHumanOutput forTesting(PrintWriter out, ConsoleStyle style, boolean quiet, boolean verbose) {
        return new CommandHumanOutput(out, style, quiet, verbose);
    }

    public static CommandHumanOutput errors(CommandSpec spec) {
        CommandLine commandLine = spec.commandLine();
        ZoltCli root = root(commandLine);
        ConsoleStyle style = root == null ? ConsoleStyle.disabled() : root.consoleStyle();
        return new CommandHumanOutput(commandLine.getErr(), style, false, false);
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

    public void summary(String headline, String... facts) {
        if (quiet) {
            return;
        }
        StringBuilder line = new StringBuilder();
        line.append(style.success("✔")).append(' ').append(headline);
        for (String fact : facts) {
            line.append(style.muted(" · " + fact));
        }
        out.println(line.toString());
    }

    public void pointer(String verb, String target) {
        if (quiet) {
            return;
        }
        out.println("  " + style.work("→") + " " + verb + " " + style.path(target));
    }

    public void pointers(String verb, String... targets) {
        for (String target : targets) {
            pointer(verb, target);
        }
    }

    public void provenance(BuildProvenance provenance) {
        if (quiet || !verbose) {
            return;
        }
        out.println("  " + style.muted("provenance"));
        commitLine(provenance.git()).ifPresent(line ->
                out.println("    " + style.muted("commit") + "    " + style.path(line)));
        out.println("    " + style.muted("built") + "     " + provenance.buildTimestamp());
        out.println("    " + style.muted("toolchain") + " "
                + "zolt " + provenance.zoltVersion()
                + " · JDK " + provenance.jdkVersion()
                + " (" + provenance.jdkVendor() + ")");
        provenance.resolutionFingerprint().ifPresent(fingerprint ->
                out.println("    " + style.muted("inputs") + "    "
                        + style.path(fingerprint) + " (resolution fingerprint)"));
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

    public void statusDetail(String marker, String message) {
        if (quiet) {
            return;
        }
        out.println(styleStatusLabel(marker) + " " + message);
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

    private String styleStatusLabel(String marker) {
        return switch (marker) {
            case "ok" -> style.success("ok:");
            case "warning" -> style.warning("warning:");
            case "error" -> style.error("error:");
            case "skip" -> style.muted("skip:");
            default -> marker + ":";
        };
    }

    private static Optional<String> commitLine(GitProvenance git) {
        if (git.commitSha().isEmpty()) {
            return Optional.empty();
        }
        StringBuilder line = new StringBuilder(git.commitSha().orElseThrow());
        git.shortSha().ifPresent(shortSha -> line.append(" (").append(shortSha).append(')'));
        git.branch().ifPresent(branch -> line.append(" on ").append(branch));
        if (git.branch().isEmpty() && git.detached()) {
            line.append(" detached");
        }
        git.dirty().filter(Boolean::booleanValue).ifPresent(ignored -> line.append(" dirty"));
        return Optional.of(line.toString());
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
