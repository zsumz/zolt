package com.zolt.cli.command.task;

import com.zolt.command.CommandConfig;
import java.nio.file.Path;

record LoadedCommandConfig(Path configPath, CommandConfig config) {
    Path root() {
        return configPath.getParent();
    }
}
