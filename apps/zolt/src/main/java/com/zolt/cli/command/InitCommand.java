package com.zolt.cli.command;

import com.zolt.cli.CommandHumanOutput;
import com.zolt.project.ProjectConfigWriteException;
import com.zolt.project.ProjectInitException;
import com.zolt.project.ProjectInitResult;
import com.zolt.project.ProjectInitializer;
import com.zolt.toml.ZoltConfigException;
import com.zolt.toml.ZoltTomlWriter;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

@Command(name = "init", description = "Create a new Zolt project.")
public final class InitCommand implements Runnable {
    private final ProjectInitializer projectInitializer;

    @Parameters(index = "0", paramLabel = "NAME", description = "Project directory to create.")
    private String name;

    @Option(names = "--group", description = "Java package group for generated sources.")
    private String group = "com.example";

    @Option(names = "--java", description = "Java version for zolt.toml.")
    private String javaVersion = "21";

    @Mixin
    private CommandProjectDirectory projectDirectory = new CommandProjectDirectory();

    @Spec
    private CommandSpec spec;

    public InitCommand() {
        this(projectInitializer());
    }

    InitCommand(ProjectInitializer projectInitializer) {
        this.projectInitializer = projectInitializer;
    }

    private static ProjectInitializer projectInitializer() {
        ZoltTomlWriter writer = new ZoltTomlWriter();
        return new ProjectInitializer((path, config) -> {
            try {
                writer.write(path, config);
            } catch (ZoltConfigException exception) {
                throw new ProjectConfigWriteException(exception.getMessage(), exception);
            }
        });
    }

    @Override
    public void run() {
        try {
            ProjectInitResult result = projectInitializer.init(projectDirectory.path(), name, group, javaVersion);
            CommandHumanOutput output = CommandHumanOutput.of(spec);
            output.success("Created Zolt project at " + result.projectDirectory());
            output.action("cd " + result.projectDirectory().getFileName());
        } catch (ProjectInitException exception) {
            throw CommandFailures.user(spec, exception);
        }
    }
}
