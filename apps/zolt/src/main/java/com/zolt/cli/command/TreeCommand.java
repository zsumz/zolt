package com.zolt.cli.command;

import com.zolt.lockfile.LockfileReadException;
import com.zolt.lockfile.ZoltLockfile;
import com.zolt.lockfile.ZoltLockfileReader;
import com.zolt.project.ProjectConfig;
import com.zolt.toml.ZoltConfigException;
import com.zolt.toml.ZoltTomlParser;
import com.zolt.tree.DependencyJsonFormatter;
import com.zolt.tree.DependencyTreeFormatter;
import java.nio.file.Path;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

@Command(name = "tree", description = "Display the resolved dependency graph.")
public final class TreeCommand implements Runnable {
    private final ZoltTomlParser tomlParser;
    private final ZoltLockfileReader lockfileReader;
    private final DependencyJsonFormatter jsonFormatter;
    private final DependencyTreeFormatter treeFormatter;

    enum Format {
        TEXT,
        JSON
    }

    @Option(names = "--cwd", hidden = true)
    private Path workingDirectory = Path.of(".");

    @Option(names = "--format", description = "Output format: text or json.")
    private Format format = Format.TEXT;

    @Spec
    private CommandSpec spec;

    public TreeCommand() {
        this(
                new ZoltTomlParser(),
                new ZoltLockfileReader(),
                new DependencyJsonFormatter(),
                new DependencyTreeFormatter());
    }

    TreeCommand(
            ZoltTomlParser tomlParser,
            ZoltLockfileReader lockfileReader,
            DependencyJsonFormatter jsonFormatter,
            DependencyTreeFormatter treeFormatter) {
        this.tomlParser = tomlParser;
        this.lockfileReader = lockfileReader;
        this.jsonFormatter = jsonFormatter;
        this.treeFormatter = treeFormatter;
    }

    @Override
    public void run() {
        try {
            ProjectConfig config = tomlParser.parse(workingDirectory.resolve("zolt.toml"));
            ZoltLockfile lockfile = lockfileReader.read(workingDirectory.resolve("zolt.lock"));
            String output = format == Format.JSON
                    ? jsonFormatter.tree(config, lockfile)
                    : treeFormatter.format(config, lockfile);
            CommandOutput.printAndFlush(spec, output);
        } catch (LockfileReadException | ZoltConfigException exception) {
            throw CommandFailures.user(spec, exception);
        }
    }
}
