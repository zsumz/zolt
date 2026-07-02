package sh.zolt.cli.surface;

import static sh.zolt.cli.CliTestSupport.execute;
import static org.junit.jupiter.api.Assertions.assertTrue;

import sh.zolt.cli.CliTestSupport.CommandResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import picocli.CommandLine;

final class CliHelpSurfaceFixtures {
    static final String ANSI_ESCAPE = "\u001B[";
    static final String BOLD_GREEN_OPTION = "\u001B[1;32m--";
    static final String BOLD_USAGE_HEADING = "\u001B[1;32mUsage:\u001B[0m";
    static final String BOLD_COMMANDS_HEADING = "\u001B[1;32mCommands:\u001B[0m";
    static final String BOLD_BASICS_HEADING = "\u001B[1;32mBasics\u001B[0m";
    static final String BOLD_CYAN_ZOLT_COMMAND = "\u001B[1;36mzolt\u001B[0m";
    static final String BOLD_CYAN_COLOR_OPTION = "\u001B[1;36m--color";
    static final String BOLD_CYAN_HELP_OPTION = "\u001B[1;36m--help\u001B[0m";
    static final String CYAN_COMMAND_ARGUMENT = "\u001B[36m[COMMAND]\u001B[0m";
    static final String HELP_COMMAND_HINT = "\u001B[1;36mzolt help\u001B[0m\u001B[36m <command>\u001B[0m";
    static final String BOLD_CYAN_INIT_COMMAND = "\u001B[1;36minit\u001B[0m";
    static final String BOLD_CYAN_SET_COMMAND = "\u001B[1;36mset\u001B[0m";
    static final String WARNING_COLOR = "\u001B[33m";
    static final String PLAIN_GREEN_OPTION = "\u001B[32m--";
    static final String HELP_COMMAND_FOOTER =
            "See 'zolt help <command>' for more information on a specific command.";

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

    static void assertContainsInOrder(String text, String... expected) {
        int previousIndex = -1;
        for (String item : expected) {
            int index = text.indexOf(item, previousIndex + 1);
            assertTrue(index > previousIndex, "Expected `" + item + "` after index " + previousIndex);
            previousIndex = index;
        }
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
            if (type.getName().startsWith("sh.zolt.cli")) {
                types.add(type);
            }
        }
        commandLine.getSubcommands().values().forEach(subcommand -> collectZoltCommandTypes(subcommand, types));
    }
}
