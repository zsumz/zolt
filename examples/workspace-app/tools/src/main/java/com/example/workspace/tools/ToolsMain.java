package com.example.workspace.tools;

import java.util.Arrays;

public final class ToolsMain {
    private ToolsMain() {
    }

    public static void main(String[] args) {
        String command = args.length == 0 ? "help" : args[0];
        String[] rest = args.length <= 1 ? new String[0] : Arrays.copyOfRange(args, 1, args.length);
        System.out.println(message(command, rest));
    }

    static String message(String command, String[] args) {
        return switch (command) {
            case "release-notes" -> "workspace tools: release notes ready";
            case "echo" -> "workspace tools: " + String.join(" ", args);
            default -> "workspace tools: available commands are release-notes and echo";
        };
    }
}
