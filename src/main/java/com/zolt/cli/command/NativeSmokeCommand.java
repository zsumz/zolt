package com.zolt.cli.command;

import com.zolt.project.ProjectConfig;
import com.zolt.selfhost.NativeSmokeException;
import com.zolt.selfhost.NativeSmokeResult;
import com.zolt.selfhost.NativeSmokeService;
import com.zolt.toml.ZoltConfigException;
import com.zolt.toml.ZoltTomlParser;
import java.nio.file.Path;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

@Command(name = "native-smoke", description = "Smoke a native Zolt binary against real workflows.")
public final class NativeSmokeCommand implements Runnable {
    @Option(names = "--binary", required = true, description = "Native Zolt binary to smoke.")
    private Path binary;

    @Option(names = "--work-dir", description = "Directory for native smoke work.")
    private Path workDirectory = Path.of("target/native-smoke");

    @Option(names = "--cwd", hidden = true)
    private Path workingDirectory = Path.of(".");

    @Spec
    private CommandSpec spec;

    @Override
    public void run() {
        try {
            ProjectConfig config = new ZoltTomlParser().parse(workingDirectory.resolve("zolt.toml"));
            NativeSmokeResult result = new NativeSmokeService().smoke(
                    workingDirectory,
                    config,
                    binary,
                    workDirectory);
            spec.commandLine().getOut().println("Native smoke status: ok");
            spec.commandLine().getOut().println("Smoked binary " + result.binary());
            spec.commandLine().getOut().println("Verified release archive " + result.archive());
            spec.commandLine().getOut().println("Ran generated project " + result.projectDirectory());
        } catch (NativeSmokeException | ZoltConfigException exception) {
            spec.commandLine().getErr().println("error: " + exception.getMessage());
            throw new CommandLine.ExecutionException(spec.commandLine(), exception.getMessage(), exception);
        }
    }
}
