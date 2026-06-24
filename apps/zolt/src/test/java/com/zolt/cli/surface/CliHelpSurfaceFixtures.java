package com.zolt.cli.surface;

import static com.zolt.cli.CliTestSupport.execute;

import com.zolt.cli.CliTestSupport.CommandResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import picocli.CommandLine;

final class CliHelpSurfaceFixtures {
    static final String ANSI_ESCAPE = "\u001B[";
    static final String BOLD_USAGE_HEADING = "\u001B[1mUsage\u001B[0m:";
    static final String BOLD_COMMANDS_HEADING = "\u001B[1mCommands\u001B[0m:";
    static final String BOLD_BASICS_HEADING = "\u001B[1mBasics\u001B[0m";
    static final String BOLD_GREEN_COLOR_OPTION = "\u001B[1;32m--color";
    static final String BOLD_GREEN_HELP_OPTION = "\u001B[1;32m--help\u001B[0m";
    static final String CYAN_HELP_COMMAND = "\u001B[36mzolt help <command>\u001B[0m";
    static final String CYAN_INIT_COMMAND = "\u001B[36minit\u001B[0m";
    static final String CYAN_SET_COMMAND = "\u001B[36mset\u001B[0m";
    static final String WARNING_COLOR = "\u001B[33m";
    static final String PLAIN_GREEN_OPTION = "\u001B[32m--";

    private CliHelpSurfaceFixtures() {
    }

    static List<List<String>> commandPaths(CommandLine root) {
        List<List<String>> paths = new ArrayList<>();
        collectCommandPaths(root, List.of(), paths);
        return paths;
    }

    static CommandResult directColorNeverHelp(List<String> path) {
        List<String> args = new ArrayList<>();
        args.add("--color=never");
        args.addAll(path);
        args.add("--help");
        return execute(args.toArray(String[]::new));
    }

    static CommandResult helpCommandColorNever(List<String> path) {
        List<String> args = new ArrayList<>();
        args.add("--color=never");
        args.add("help");
        args.addAll(path);
        return execute(args.toArray(String[]::new));
    }

    static List<String> topLevelCommandNames(CommandLine root) {
        return root.getSubcommands().keySet().stream()
                .filter(command -> !command.equals("help"))
                .toList();
    }

    static List<Class<?>> zoltCommandTypes(CommandLine root) {
        List<Class<?>> types = new ArrayList<>();
        collectZoltCommandTypes(root, types);
        return types;
    }

    private static void collectCommandPaths(CommandLine commandLine, List<String> prefix, List<List<String>> paths) {
        paths.add(prefix);
        for (Map.Entry<String, CommandLine> entry : commandLine.getSubcommands().entrySet()) {
            List<String> childPath = new ArrayList<>(prefix);
            childPath.add(entry.getKey());
            collectCommandPaths(entry.getValue(), childPath, paths);
        }
    }

    private static void collectZoltCommandTypes(CommandLine commandLine, List<Class<?>> types) {
        Object userObject = commandLine.getCommandSpec().userObject();
        if (userObject != null) {
            Class<?> type = userObject instanceof Class<?> commandClass ? commandClass : userObject.getClass();
            if (type.getName().startsWith("com.zolt.cli")) {
                types.add(type);
            }
        }
        commandLine.getSubcommands().values().forEach(subcommand -> collectZoltCommandTypes(subcommand, types));
    }
}
