package com.zolt.cli.command.task;

import com.zolt.cli.CommandHumanOutput;
import com.zolt.cli.command.CommandFailures;
import com.zolt.cli.command.CommandProjectDirectory;
import com.zolt.command.CommandAlias;
import com.zolt.toml.ZoltConfigException;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

@Command(name = "aliases", description = "List configured command aliases.")
public final class AliasesCommand implements Runnable {
    private final TaskCommandConfigLoader configLoader;

    @Mixin
    private CommandProjectDirectory projectDirectory = new CommandProjectDirectory();

    @Spec
    private CommandSpec spec;

    public AliasesCommand() {
        this(new TaskCommandConfigLoader());
    }

    AliasesCommand(TaskCommandConfigLoader configLoader) {
        this.configLoader = configLoader;
    }

    @Override
    public void run() {
        try {
            LoadedCommandConfig loaded = configLoader.load(projectDirectory, spec);
            CommandHumanOutput output = CommandHumanOutput.of(spec);
            if (loaded.config().aliases().isEmpty()) {
                output.detail("No aliases configured");
                output.next("Add [commands.aliases] to " + loaded.configPath());
                return;
            }

            output.line("Aliases:");
            for (CommandAlias alias : loaded.config().aliases().values()) {
                output.line("  " + padded(alias.name()) + String.join(" ", alias.argv()));
            }
        } catch (ZoltConfigException exception) {
            throw CommandFailures.user(spec, exception);
        }
    }

    private static String padded(String value) {
        int width = 20;
        if (value.length() >= width) {
            return value + "  ";
        }
        return value + " ".repeat(width - value.length());
    }
}
