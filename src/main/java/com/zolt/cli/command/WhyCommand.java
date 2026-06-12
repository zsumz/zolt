package com.zolt.cli.command;

import com.zolt.lockfile.LockfileReadException;
import com.zolt.lockfile.ZoltLockfile;
import com.zolt.lockfile.ZoltLockfileReader;
import com.zolt.maven.Coordinate;
import com.zolt.maven.CoordinateParseException;
import com.zolt.maven.CoordinateParser;
import com.zolt.project.ProjectConfig;
import com.zolt.resolve.PackageId;
import com.zolt.toml.ZoltConfigException;
import com.zolt.toml.ZoltTomlParser;
import com.zolt.tree.DependencyJsonFormatter;
import com.zolt.tree.DependencyWhyException;
import com.zolt.tree.DependencyWhyFormatter;
import java.nio.file.Path;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

@Command(name = "why", description = "Explain why a package is present.")
public final class WhyCommand implements Runnable {
    enum Format {
        TEXT,
        JSON
    }

    @Parameters(index = "0", paramLabel = "GROUP:ARTIFACT", description = "Package id to explain.")
    private String packageId;

    @Option(names = "--cwd", hidden = true)
    private Path workingDirectory = Path.of(".");

    @Option(names = "--format", description = "Output format: text or json.")
    private Format format = Format.TEXT;

    @Spec
    private CommandSpec spec;

    private final CoordinateParser coordinateParser = new CoordinateParser();

    @Override
    public void run() {
        try {
            Coordinate coordinate = coordinateParser.parse(packageId);
            ProjectConfig config = new ZoltTomlParser().parse(workingDirectory.resolve("zolt.toml"));
            ZoltLockfile lockfile = new ZoltLockfileReader().read(workingDirectory.resolve("zolt.lock"));
            PackageId target = new PackageId(coordinate.groupId(), coordinate.artifactId());
            String output = format == Format.JSON
                    ? new DependencyJsonFormatter().why(config, lockfile, target)
                    : new DependencyWhyFormatter().format(config, lockfile, target);
            CommandOutput.printAndFlush(spec, output);
        } catch (CoordinateParseException | DependencyWhyException | LockfileReadException | ZoltConfigException exception) {
            spec.commandLine().getErr().println("error: " + exception.getMessage());
            throw new CommandLine.ExecutionException(spec.commandLine(), exception.getMessage(), exception);
        }
    }
}
