package com.zolt.cli.console;

import java.util.Map;

public final class ConsoleStyle {
    private static final String RESET = "\u001B[0m";
    private static final String BOLD = "\u001B[1m";
    private static final String BOLD_GREEN = "\u001B[1;32m";
    private static final String BOLD_CYAN = "\u001B[1;36m";
    private static final String DIM = "\u001B[2m";
    private static final String RED = "\u001B[31m";
    private static final String GREEN = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String CYAN = "\u001B[36m";

    private final boolean enabled;

    private ConsoleStyle(boolean enabled) {
        this.enabled = enabled;
    }

    public static ConsoleStyle of(ColorMode mode, boolean interactive, Map<String, String> environment) {
        return new ConsoleStyle(mode.enabled(interactive, environment));
    }

    public static ConsoleStyle enabled() {
        return new ConsoleStyle(true);
    }

    public static ConsoleStyle disabled() {
        return new ConsoleStyle(false);
    }

    public boolean enabledColor() {
        return enabled;
    }

    public String heading(String text) {
        return style(BOLD, text);
    }

    public String helpHeading(String text) {
        return style(BOLD_GREEN, text);
    }

    public String command(String text) {
        return style(CYAN, text);
    }

    public String helpCommand(String text) {
        return style(BOLD_CYAN, text);
    }

    public String helpMeta(String text) {
        return style(CYAN, text);
    }

    public String path(String text) {
        return style(CYAN, text);
    }

    public String success(String text) {
        return style(GREEN, text);
    }

    public String option(String text) {
        return style(BOLD_CYAN, text);
    }

    public String work(String text) {
        return style(CYAN, text);
    }

    public String warning(String text) {
        return style(YELLOW, text);
    }

    public String error(String text) {
        return style(RED, text);
    }

    public String muted(String text) {
        return style(DIM, text);
    }

    private String style(String ansi, String text) {
        if (!enabled || text.isEmpty()) {
            return text;
        }
        return ansi + text + RESET;
    }
}
