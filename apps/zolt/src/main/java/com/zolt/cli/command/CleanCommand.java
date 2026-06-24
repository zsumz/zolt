package com.zolt.cli.command;

import com.zolt.build.CleanException;
import com.zolt.build.CleanResult;
import com.zolt.build.CleanService;
import com.zolt.project.ProjectConfig;
import com.zolt.toml.ZoltConfigException;
import com.zolt.toml.ZoltTomlParser;
import java.nio.file.Path;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

@Command(name = "clean", description = "Remove project build output.")
public final class CleanCommand implements Runnable {
    private final ZoltTomlParser tomlParser;
    private final CleanService cleanService;

    @Mixin
    private CommandProjectDirectory projectDirectory = new CommandProjectDirectory();

    @Spec
    private CommandSpec spec;

    public CleanCommand() {
        this(new ZoltTomlParser(), new CleanService());
    }

    CleanCommand(ZoltTomlParser tomlParser, CleanService cleanService) {
        this.tomlParser = tomlParser;
        this.cleanService = cleanService;
    }

    @Override
    public void run() {
        try {
            Path projectRoot = projectDirectory.path();
            ProjectConfig config = tomlParser.parse(projectRoot.resolve("zolt.toml"));
            CleanResult result = cleanService.clean(projectRoot, config);
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
