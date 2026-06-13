package com.zolt.cli.command;

import com.zolt.project.ProjectConfig;
import com.zolt.selfhost.NativeSmokeException;
import com.zolt.selfhost.NativeSmokeResult;
import com.zolt.selfhost.NativeSmokeService;
import com.zolt.toml.ZoltConfigException;
import com.zolt.toml.ZoltTomlParser;
import java.nio.file.Path;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

@Command(name = "native-smoke", description = "Smoke a native Zolt binary against real workflows.")
public final class NativeSmokeCommand implements Runnable {
    private final ZoltTomlParser tomlParser;
    private final NativeSmokeService nativeSmokeService;

    @Option(names = "--binary", required = true, description = "Native Zolt binary to smoke.")
    private Path binary;

    @Option(names = "--work-dir", description = "Directory for native smoke work.")
    private Path workDirectory = Path.of("target/native-smoke");

    @Option(names = "--cwd", hidden = true)
    private Path workingDirectory = Path.of(".");

    @Spec
    private CommandSpec spec;

    public NativeSmokeCommand() {
        this(new ZoltTomlParser(), new NativeSmokeService());
    }

    NativeSmokeCommand(ZoltTomlParser tomlParser, NativeSmokeService nativeSmokeService) {
        this.tomlParser = tomlParser;
        this.nativeSmokeService = nativeSmokeService;
    }

    @Override
    public void run() {
        try {
            ProjectConfig config = tomlParser.parse(workingDirectory.resolve("zolt.toml"));
            NativeSmokeResult result = nativeSmokeService.smoke(
                    workingDirectory,
                    config,
                    binary,
                    workDirectory);
            spec.commandLine().getOut().println("Native smoke status: ok");
            spec.commandLine().getOut().println("Smoked binary " + result.binary());
            spec.commandLine().getOut().println("Verified release archive " + result.archive());
            spec.commandLine().getOut().println("Ran generated project " + result.projectDirectory());
        } catch (NativeSmokeException | ZoltConfigException exception) {
            throw CommandFailures.user(spec, exception);
        }
    }
}
