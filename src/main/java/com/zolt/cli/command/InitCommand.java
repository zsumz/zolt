package com.zolt.cli.command;

import com.zolt.project.ProjectInitException;
import com.zolt.project.ProjectInitResult;
import com.zolt.project.ProjectInitializer;
import java.nio.file.Path;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

@Command(name = "init", description = "Create a new Zolt project.")
public final class InitCommand implements Runnable {
    @Parameters(index = "0", paramLabel = "NAME", description = "Project directory to create.")
    private String name;

    @Option(names = "--group", description = "Java package group for generated sources.")
    private String group = "com.example";

    @Option(names = "--java", description = "Java version for zolt.toml.")
    private String javaVersion = "21";

    @Option(names = "--cwd", hidden = true)
    private Path workingDirectory = Path.of(".");

    @Spec
    private CommandSpec spec;

    @Override
    public void run() {
        try {
            ProjectInitResult result = new ProjectInitializer().init(workingDirectory, name, group, javaVersion);
            spec.commandLine().getOut().println("Created Zolt project at " + result.projectDirectory());
            spec.commandLine().getOut().println("Next: cd " + result.projectDirectory().getFileName());
        } catch (ProjectInitException exception) {
            spec.commandLine().getErr().println("error: " + exception.getMessage());
            throw new CommandLine.ExecutionException(spec.commandLine(), exception.getMessage(), exception);
        }
    }
}
