package com.zolt.cli.command;

import com.zolt.cache.ArtifactCacheException;
import com.zolt.cache.LocalArtifactCache;
import com.zolt.cli.command.DependencyEditCommands.DependencySectionException;
import com.zolt.cli.command.DependencyEditCommands.RemoveCommandException;
import com.zolt.cli.command.DependencyEditCommands.RemoveRequest;
import com.zolt.maven.Coordinate;
import com.zolt.maven.CoordinateParseException;
import com.zolt.maven.CoordinateParser;
import com.zolt.project.DependencySection;
import com.zolt.project.ProjectConfig;
import com.zolt.resolve.ResolveException;
import com.zolt.resolve.ResolveService;
import com.zolt.toml.ZoltConfigException;
import com.zolt.toml.ZoltTomlParser;
import com.zolt.toml.ZoltTomlWriter;
import java.nio.file.Path;
import java.util.List;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

@Command(
        name = "remove",
        description = "Remove a dependency and prune unused transitive packages.",
        mixinStandardHelpOptions = true)
public final class RemoveCommand implements Runnable {
    private final CoordinateParser coordinateParser;
    private final ZoltTomlParser tomlParser;
    private final ZoltTomlWriter tomlWriter;
    private final ResolveService resolveService;

    @Parameters(
            arity = "1..2",
            paramLabel = "DEPENDENCY",
            description = "Dependency coordinate. May be prefixed with api, runtime, provided, dev, test, processor, or test-processor.")
    private List<String> arguments;

    @Mixin
    private CommandProjectDirectory projectDirectory = new CommandProjectDirectory();

    @Option(names = "--cache-root", hidden = true)
    private Path cacheRoot = LocalArtifactCache.defaultRoot();

    @Spec
    private CommandSpec spec;

    public RemoveCommand() {
        this(new CoordinateParser(), new ZoltTomlParser(), new ZoltTomlWriter(), CommandFrameworkServices.resolveService());
    }

    RemoveCommand(
            CoordinateParser coordinateParser,
            ZoltTomlParser tomlParser,
            ZoltTomlWriter tomlWriter,
            ResolveService resolveService) {
        this.coordinateParser = coordinateParser;
        this.tomlParser = tomlParser;
        this.tomlWriter = tomlWriter;
        this.resolveService = resolveService;
    }

    @Override
    public void run() {
        try {
            RemoveRequest request = parseRequest(arguments);
            Path projectRoot = projectDirectory.path();
            Path configPath = projectRoot.resolve("zolt.toml");
            ProjectConfig config = tomlParser.parse(configPath);
            String section = DependencyEditCommands.sectionName(request.section());
            if (!DependencyEditCommands.hasDependency(config, request.section(), request.coordinate())) {
                spec.commandLine().getOut().println("Dependency " + request.coordinate()
                        + " is not present in [" + section + "]; nothing to remove.");
                return;
            }
            ProjectConfig updated = tomlWriter.removeDependency(config, request.section(), request.coordinate());
            tomlWriter.write(configPath, updated);
            spec.commandLine().getOut().println(
                    "Removed dependency " + request.coordinate() + " from [" + section + "]");
            CommandResolveOutput.print(spec, resolveService.resolve(projectRoot, updated, cacheRoot));
        } catch (RemoveCommandException
                | DependencySectionException
                | ArtifactCacheException
                | CoordinateParseException
                | ResolveException
                | ZoltConfigException exception) {
            throw CommandFailures.user(spec, exception);
        }
    }

    private RemoveRequest parseRequest(List<String> values) {
        DependencySection section = DependencyEditCommands.parseSection(values, "zolt remove");
        String rawCoordinate = values.size() == 2 ? values.get(1) : values.get(0);
        Coordinate coordinate = coordinateParser.parse(rawCoordinate);
        return new RemoveRequest(section, coordinate.groupId() + ":" + coordinate.artifactId());
    }
}
