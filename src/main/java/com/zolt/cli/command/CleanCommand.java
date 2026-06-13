package com.zolt.cli.command;

import com.zolt.build.CleanException;
import com.zolt.build.CleanResult;
import com.zolt.build.CleanService;
import com.zolt.project.ProjectConfig;
import com.zolt.toml.ZoltConfigException;
import com.zolt.toml.ZoltTomlParser;
import java.nio.file.Path;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

@Command(name = "clean", description = "Remove project build output.")
public final class CleanCommand implements Runnable {
    @Option(names = "--cwd", hidden = true)
    private Path workingDirectory = Path.of(".");

    @Spec
    private CommandSpec spec;

    @Override
    public void run() {
        try {
            ProjectConfig config = new ZoltTomlParser().parse(workingDirectory.resolve("zolt.toml"));
            CleanResult result = new CleanService().clean(workingDirectory, config);
            if (result.deletedPaths().isEmpty()) {
                spec.commandLine().getOut().println("Nothing to clean");
                return;
            }
            spec.commandLine().getOut().println("Deleted " + result.deletedCount() + " build output paths");
            for (Path path : result.deletedPaths()) {
                spec.commandLine().getOut().println("Deleted " + path);
            }
        } catch (CleanException | ZoltConfigException exception) {
            throw CommandFailures.user(spec, exception);
        }
    }
}
