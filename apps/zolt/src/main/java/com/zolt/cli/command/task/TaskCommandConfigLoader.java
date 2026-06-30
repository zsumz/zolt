package com.zolt.cli.command.task;

import com.zolt.command.toml.CommandConfigParser;
import com.zolt.cli.command.CommandProjectDirectory;
import java.nio.file.Path;
import picocli.CommandLine.Model.CommandSpec;

final class TaskCommandConfigLoader {
    private final CommandConfigRoots roots;

    TaskCommandConfigLoader() {
        this(new CommandConfigRoots());
    }

    TaskCommandConfigLoader(CommandConfigRoots roots) {
        this.roots = roots;
    }

    LoadedCommandConfig load(CommandProjectDirectory projectDirectory, CommandSpec spec) {
        Path configPath = roots.discoverConfig(projectDirectory.path());
        CommandConfigParser parser = new CommandConfigParser(CommandConfigRoots.builtInCommandNames(spec));
        return new LoadedCommandConfig(configPath, parser.parse(configPath));
    }
}
