package com.zolt.command;

import com.zolt.toml.ZoltConfigException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.tomlj.Toml;
import org.tomlj.TomlArray;
import org.tomlj.TomlParseError;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;

public final class CommandConfigParser {
    private static final Set<String> COMMANDS_KEYS = Set.of("aliases", "tasks");
    private static final Set<String> TASK_KEYS = Set.of("description", "cmd", "cwd", "env");

    private final Set<String> builtInCommandNames;

    public CommandConfigParser(Set<String> builtInCommandNames) {
        if (builtInCommandNames == null || builtInCommandNames.isEmpty()) {
            throw new IllegalArgumentException("builtInCommandNames must not be empty");
        }
        this.builtInCommandNames = Collections.unmodifiableSet(new LinkedHashSet<>(builtInCommandNames));
    }

    public CommandConfig parse(Path path) {
        try {
            return parse(Toml.parse(path));
        } catch (IOException exception) {
            throw new ZoltConfigException(
                    "Could not read zolt.toml at " + path + ". Check that the file exists and is readable.");
        }
    }

    public CommandConfig parse(String content) {
        return parse(Toml.parse(content));
    }

    private CommandConfig parse(TomlParseResult result) {
        if (result.hasErrors()) {
            throw new ZoltConfigException(parseErrorMessage(result));
        }

        TomlTable commandsTable = result.getTable("commands");
        if (commandsTable == null) {
            return CommandConfig.empty();
        }
        validateKeys("commands", commandsTable, COMMANDS_KEYS);

        Map<String, CommandTask> tasks = parseTasks(commandsTable);
        Map<String, CommandAlias> aliases = parseAliases(commandsTable, tasks.keySet());
        return new CommandConfig(aliases, tasks);
    }

    private Map<String, CommandTask> parseTasks(TomlTable commandsTable) {
        TomlTable tasksTable = optionalTable(commandsTable, "commands", "tasks", "Use tables such as [commands.tasks.fmt].");
        if (tasksTable == null) {
            return Map.of();
        }

        Map<String, CommandTask> tasks = new LinkedHashMap<>();
        for (String taskName : tasksTable.keySet()) {
            validateTaskName(taskName);
            TomlTable taskTable = requiredNestedTable(
                    tasksTable,
                    "commands.tasks",
                    taskName,
                    "Use a table such as [commands.tasks." + taskName + "].");
            String section = "commands.tasks." + taskName;
            validateKeys(section, taskTable, TASK_KEYS);
            Optional<String> description = optionalNonBlankString(taskTable, section, "description");
            List<String> cmd = requiredStringList(taskTable, section, "cmd", "Add cmd = [\"executable\", \"...\"].");
            Optional<String> cwd = optionalCwd(taskTable, section);
            Map<String, String> env = parseEnv(taskTable, section);
            tasks.put(taskName, new CommandTask(taskName, description, cmd, cwd, env));
        }
        return tasks;
    }

    private Map<String, CommandAlias> parseAliases(TomlTable commandsTable, Set<String> taskNames) {
        TomlTable aliasesTable = optionalTable(commandsTable, "commands", "aliases", "Use [commands.aliases] with argv arrays.");
        if (aliasesTable == null) {
            return Map.of();
        }

        Map<String, CommandAlias> aliases = new LinkedHashMap<>();
        for (String aliasName : aliasesTable.keySet()) {
            validateAliasName(aliasName, taskNames);
            List<String> argv = requiredStringList(
                    aliasesTable,
                    "commands.aliases",
                    aliasName,
                    "Use a non-empty array whose first value is a built-in Zolt command.");
            validateAliasArgv(aliasName, argv);
            aliases.put(aliasName, new CommandAlias(aliasName, argv));
        }
        return aliases;
    }

    private void validateAliasName(String aliasName, Set<String> taskNames) {
        if (!CommandConfigRules.isValidName(aliasName)) {
            throw new ZoltConfigException(
                    "Invalid [commands.aliases] alias `"
                            + aliasName
                            + "` in zolt.toml. Alias names may contain only letters, digits, dot, underscore, and hyphen, and must not start with hyphen.");
        }
        if (builtInCommandNames.contains(aliasName)) {
            throw new ZoltConfigException(
                    "Invalid [commands.aliases] alias `"
                            + aliasName
                            + "` in zolt.toml. Aliases cannot replace built-in Zolt commands.");
        }
        if (taskNames.contains(aliasName)) {
            throw new ZoltConfigException(
                    "Invalid [commands.aliases] alias `"
                            + aliasName
                            + "` in zolt.toml. Alias names must not equal task names; run tasks explicitly with `zolt task "
                            + aliasName
                            + "`.");
        }
    }

    private void validateTaskName(String taskName) {
        if (!CommandConfigRules.isValidName(taskName)) {
            throw new ZoltConfigException(
                    "Invalid [commands.tasks] task name `"
                            + taskName
                            + "` in zolt.toml. Task names may contain only letters, digits, dot, underscore, and hyphen, and must not start with hyphen.");
        }
        if (builtInCommandNames.contains(taskName)) {
            throw new ZoltConfigException(
                    "Invalid [commands.tasks] task `"
                            + taskName
                            + "` in zolt.toml. Tasks cannot replace built-in Zolt commands.");
        }
    }

    private void validateAliasArgv(String aliasName, List<String> argv) {
        for (int index = 0; index < argv.size(); index++) {
            String value = argv.get(index);
            if (CommandConfigRules.isEnvironmentAssignmentPrefix(value)) {
                throw new ZoltConfigException(
                        "Invalid [commands.aliases]."
                                + aliasName
                                + "["
                                + index
                                + "] in zolt.toml. Aliases cannot include environment assignment prefixes; put external commands in [commands.tasks].");
            }
            if (CommandConfigRules.looksLikeShellString(value)) {
                throw new ZoltConfigException(
                        "Invalid [commands.aliases]."
                                + aliasName
                                + "["
                                + index
                                + "] in zolt.toml. Aliases cannot use shell strings; put external commands in [commands.tasks].");
            }
        }

        String target = argv.getFirst();
        if ("task".equals(target)) {
            throw new ZoltConfigException(
                    "Invalid [commands.aliases]."
                            + aliasName
                            + " in zolt.toml. Alias targets cannot be `task`; tasks must be run explicitly with `zolt task <name>`.");
        }
        if (CommandConfigRules.isShellExecutable(target)) {
            throw new ZoltConfigException(
                    "Invalid [commands.aliases]."
                            + aliasName
                            + " in zolt.toml. Aliases cannot run shells; put external commands in [commands.tasks].");
        }
        if (CommandConfigRules.isExecutablePath(target)) {
            throw new ZoltConfigException(
                    "Invalid [commands.aliases]."
                            + aliasName
                            + " in zolt.toml. Alias target `"
                            + target
                            + "` looks like an executable path; external programs belong in [commands.tasks].");
        }
        if (!builtInCommandNames.contains(target)) {
            throw new ZoltConfigException(
                    "Invalid [commands.aliases]."
                            + aliasName
                            + " in zolt.toml. Alias target `"
                            + target
                            + "` is not a built-in Zolt command.");
        }
    }

    private static Map<String, String> parseEnv(TomlTable taskTable, String section) {
        TomlTable envTable = optionalTable(taskTable, section, "env", "Use env = { KEY = \"literal value\" }.");
        if (envTable == null) {
            return Map.of();
        }

        Map<String, String> values = new LinkedHashMap<>();
        for (String key : envTable.keySet()) {
            if (!CommandConfigRules.isValidEnvironmentName(key)) {
                throw new ZoltConfigException(
                        "Invalid environment variable name ["
                                + section
                                + ".env]."
                                + key
                                + " in zolt.toml. Use names like APP_ENV.");
            }
            Object rawValue = envTable.get(List.of(key));
            if (!(rawValue instanceof String value)) {
                throw new ZoltConfigException(
                        "Invalid value for ["
                                + section
                                + ".env]."
                                + key
                                + " in zolt.toml. Use a literal string value.");
            }
            values.put(key, value);
        }
        return values;
    }

    private static Optional<String> optionalCwd(TomlTable table, String section) {
        Optional<String> value = optionalNonBlankString(table, section, "cwd");
        if (value.isPresent() && !CommandConfigRules.isRelativeNormalizedPath(value.orElseThrow())) {
            throw new ZoltConfigException(
                    "Invalid value for ["
                            + section
                            + "].cwd in zolt.toml. Use a relative normalized path that stays under the command config root.");
        }
        return value;
    }

    private static Optional<String> optionalNonBlankString(TomlTable table, String section, String key) {
        Object rawValue = table.get(List.of(key));
        if (rawValue == null) {
            return Optional.empty();
        }
        if (!(rawValue instanceof String value) || value.isBlank()) {
            throw new ZoltConfigException(
                    "Invalid value for [" + section + "]." + key + " in zolt.toml. Use a non-empty string value.");
        }
        return Optional.of(value);
    }

    private static List<String> requiredStringList(
            TomlTable table,
            String section,
            String key,
            String hint) {
        Object rawValue = table.get(List.of(key));
        if (rawValue == null) {
            throw new ZoltConfigException(
                    "Missing required field [" + section + "]." + key + " in zolt.toml. " + hint);
        }
        if (!(rawValue instanceof TomlArray array)) {
            throw new ZoltConfigException(
                    "Invalid value for [" + section + "]." + key + " in zolt.toml. Use an array of strings.");
        }
        if (array.isEmpty()) {
            throw new ZoltConfigException(
                    "Invalid value for [" + section + "]." + key + " in zolt.toml. Use a non-empty array of strings.");
        }

        List<String> values = new ArrayList<>();
        for (int index = 0; index < array.size(); index++) {
            Object element = array.get(index);
            if (!(element instanceof String value) || value.isBlank()) {
                throw new ZoltConfigException(
                        "Invalid value for ["
                                + section
                                + "]."
                                + key
                                + "["
                                + index
                                + "] in zolt.toml. Use a non-empty string.");
            }
            values.add(value);
        }
        return List.copyOf(values);
    }

    private static TomlTable optionalTable(TomlTable table, String section, String key, String hint) {
        Object rawValue = table.get(List.of(key));
        if (rawValue == null) {
            return null;
        }
        if (rawValue instanceof TomlTable nestedTable) {
            return nestedTable;
        }
        throw new ZoltConfigException(
                "Invalid value for [" + section + "]." + key + " in zolt.toml. " + hint);
    }

    private static TomlTable requiredNestedTable(TomlTable table, String section, String key, String hint) {
        Object rawValue = table.get(List.of(key));
        if (rawValue instanceof TomlTable nestedTable) {
            return nestedTable;
        }
        throw new ZoltConfigException(
                "Invalid value for [" + section + "]." + key + " in zolt.toml. " + hint);
    }

    private static void validateKeys(String section, TomlTable table, Set<String> allowedKeys) {
        for (String key : table.keySet()) {
            if (!allowedKeys.contains(key)) {
                throw new ZoltConfigException(
                        "Unknown field [" + section + "]." + key + " in zolt.toml. Remove it or check the spelling.");
            }
        }
    }

    private static String parseErrorMessage(TomlParseResult result) {
        TomlParseError firstError = result.errors().getFirst();
        return "Could not parse zolt.toml. Fix the TOML syntax near "
                + firstError.position()
                + ": "
                + firstError.getMessage();
    }
}
