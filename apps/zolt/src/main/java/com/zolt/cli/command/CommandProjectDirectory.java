package com.zolt.cli.command;

import java.nio.file.Path;
import picocli.CommandLine.Option;

public final class CommandProjectDirectory {
    @Option(names = "--cwd", hidden = true)
    private Path workingDirectory = Path.of(".");

    @Option(
            names = "--directory",
            paramLabel = "<DIRECTORY>",
            description = "Run as if Zolt was started in the given project directory.")
    private Path directory;

    public Path path() {
        return directory == null ? workingDirectory : directory;
    }
}
