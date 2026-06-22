package com.zolt.cli.command;

import com.zolt.lockfile.LockfileReadException;
import com.zolt.lockfile.ZoltLockfile;
import com.zolt.lockfile.ZoltLockfileReader;
import com.zolt.maven.Coordinate;
import com.zolt.maven.CoordinateParseException;
import com.zolt.maven.CoordinateParser;
import com.zolt.project.ProjectConfig;
import com.zolt.dependency.PackageId;
import com.zolt.toml.ZoltConfigException;
import com.zolt.toml.ZoltTomlParser;
import com.zolt.tree.DependencyJsonFormatter;
import com.zolt.tree.DependencyWhyException;
import com.zolt.tree.DependencyWhyFormatter;
import java.nio.file.Path;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

@Command(name = "why", description = "Explain why a package is present.")
public final class WhyCommand implements Runnable {
    private final CoordinateParser coordinateParser;
    private final ZoltTomlParser tomlParser;
    private final ZoltLockfileReader lockfileReader;
    private final DependencyJsonFormatter jsonFormatter;
    private final DependencyWhyFormatter whyFormatter;

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

    public WhyCommand() {
        this(
                new CoordinateParser(),
                new ZoltTomlParser(),
                new ZoltLockfileReader(),
                new DependencyJsonFormatter(),
                new DependencyWhyFormatter());
    }

    WhyCommand(
            CoordinateParser coordinateParser,
            ZoltTomlParser tomlParser,
            ZoltLockfileReader lockfileReader,
            DependencyJsonFormatter jsonFormatter,
            DependencyWhyFormatter whyFormatter) {
        this.coordinateParser = coordinateParser;
        this.tomlParser = tomlParser;
        this.lockfileReader = lockfileReader;
        this.jsonFormatter = jsonFormatter;
        this.whyFormatter = whyFormatter;
    }

    @Override
    public void run() {
        try {
            Coordinate coordinate = coordinateParser.parse(packageId);
            ProjectConfig config = tomlParser.parse(workingDirectory.resolve("zolt.toml"));
            ZoltLockfile lockfile = lockfileReader.read(workingDirectory.resolve("zolt.lock"));
            PackageId target = new PackageId(coordinate.groupId(), coordinate.artifactId());
            String output = format == Format.JSON
                    ? jsonFormatter.why(config, lockfile, target)
                    : whyFormatter.format(config, lockfile, target);
            CommandOutput.printAndFlush(spec, output);
        } catch (CoordinateParseException | DependencyWhyException | LockfileReadException | ZoltConfigException exception) {
            throw CommandFailures.user(spec, exception);
        }
    }
}
