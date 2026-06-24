package com.zolt.cli.command;

import java.nio.file.Path;
import picocli.CommandLine.Option;

final class CommandProjectDirectory {
    @Option(names = "--cwd", hidden = true)
    private Path workingDirectory = Path.of(".");

    @Option(names = "--directory", description = "Run as if Zolt was started in the given project directory.")
    private Path directory;

    Path path() {
        return directory == null ? workingDirectory : directory;
    }
}
