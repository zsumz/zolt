package com.zolt.cli.command;

import com.zolt.cache.ArtifactCacheException;
import com.zolt.cache.LocalArtifactCache;
import com.zolt.cli.command.DependencyEditCommands.AddCommandException;
import com.zolt.cli.command.DependencyEditCommands.AddRequest;
import com.zolt.cli.command.DependencyEditCommands.DependencySectionException;
import com.zolt.maven.Coordinate;
import com.zolt.maven.CoordinateParseException;
import com.zolt.maven.CoordinateParser;
import com.zolt.project.DependencySection;
import com.zolt.project.ProjectConfig;
import com.zolt.project.VersionPolicy;
import com.zolt.resolve.ResolveException;
import com.zolt.resolve.ResolveService;
import com.zolt.toml.ZoltConfigException;
import com.zolt.toml.ZoltTomlParser;
import com.zolt.toml.ZoltTomlWriter;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

@Command(name = "add", description = "Add a dependency to zolt.toml and refresh zolt.lock.", mixinStandardHelpOptions = true)
public final class AddCommand implements Runnable {
    private final CoordinateParser coordinateParser;
    private final ZoltTomlParser tomlParser;
    private final ZoltTomlWriter tomlWriter;
    private final ResolveService resolveService;

    @Parameters(
            arity = "1..2",
            paramLabel = "DEPENDENCY",
            description = "Dependency coordinate. May be prefixed with api, runtime, provided, dev, test, processor, or test-processor.")
    private List<String> arguments;

    @Option(names = "--managed", description = "Use a version managed by a declared platform.")
    private boolean managed;

    @Option(names = "--version-ref", description = "Use a version alias declared in [versions].")
    private String versionRef;

    @Option(names = "--no-resolve", description = "Update zolt.toml without refreshing zolt.lock.")
    private boolean noResolve;

    @Mixin
    private CommandProjectDirectory projectDirectory = new CommandProjectDirectory();

    @Option(names = "--cache-root", hidden = true)
    private Path cacheRoot = LocalArtifactCache.defaultRoot();

    @Spec
    private CommandSpec spec;

    public AddCommand() {
        this(new CoordinateParser(), new ZoltTomlParser(), new ZoltTomlWriter(), CommandFrameworkServices.resolveService());
    }

    AddCommand(
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
            AddRequest request = parseRequest(arguments);
            Path projectRoot = projectDirectory.path();
            Path configPath = projectRoot.resolve("zolt.toml");
            ProjectConfig config = tomlParser.parse(configPath);
            ProjectConfig updated = updateConfig(config, request);
            tomlWriter.write(configPath, updated);
            printAddSummary(config, request);
            if (noResolve) {
                spec.commandLine().getOut().println("Skipped resolve; run zolt resolve to refresh zolt.lock.");
                return;
            }
            CommandResolveOutput.print(spec, resolveService.resolve(projectRoot, updated, cacheRoot));
        } catch (AddCommandException
                | DependencySectionException
                | ArtifactCacheException
                | CoordinateParseException
                | ResolveException
                | ZoltConfigException exception) {
            throw CommandFailures.user(spec, exception);
        }
    }

    private AddRequest parseRequest(List<String> values) {
        DependencySection section = DependencyEditCommands.parseSection(values, "zolt add");
        String rawCoordinate = values.size() == 2 ? values.get(1) : values.get(0);
        Coordinate coordinate = coordinateParser.parse(rawCoordinate);
        if (managed && coordinate.version().isPresent()) {
            throw new AddCommandException(
                    "Managed dependency coordinate must not include a version. Use `group:artifact`.");
        }
        if (versionRef != null && versionRef.isBlank()) {
            throw new AddCommandException(
                    "Version alias for --version-ref must be non-empty. Use `--version-ref <alias>`.");
        }
        if (managed && versionRef != null) {
            throw new AddCommandException(
                    "`--managed` and `--version-ref` cannot be used together. Choose a platform-managed dependency or a named [versions] alias.");
        }
        if (versionRef != null && coordinate.version().isPresent()) {
            throw new AddCommandException(
                    "Version-ref dependency coordinate must not include a version. Use `--version-ref "
                            + versionRef
                            + " group:artifact`.");
        }
        if (managed) {
            return new AddRequest(section, coordinate.groupId() + ":" + coordinate.artifactId(), "", true, null);
        }
        if (versionRef != null) {
            return new AddRequest(section, coordinate.groupId() + ":" + coordinate.artifactId(), "", false, versionRef);
        }
        String version = coordinate.version().orElseThrow(() -> new AddCommandException(
                "Dependency coordinate must include a version. Use `group:artifact:version` or add `--managed` when a declared platform should provide the version."));
        DependencyEditCommands.validateCommandVersion(
                VersionPolicy.Context.EXTERNAL_DEPENDENCY,
                "dependency",
                version,
                AddCommandException::new);
        return new AddRequest(section, coordinate.groupId() + ":" + coordinate.artifactId(), version, false, null);
    }

    private ProjectConfig updateConfig(ProjectConfig config, AddRequest request) {
        if (request.managed()) {
            return tomlWriter.addManagedDependency(config, request.section(), request.coordinate());
        }
        if (request.versionRef() != null) {
            String version = config.versionAliases().get(request.versionRef());
            if (version == null) {
                throw new AddCommandException(
                        "Unknown versionRef `"
                                + request.versionRef()
                                + "`. Add [versions]."
                                + request.versionRef()
                                + " or use an explicit version.");
            }
            return tomlWriter.addVersionRefDependency(
                    config,
                    request.section(),
                    request.coordinate(),
                    request.versionRef(),
                    version);
        }
        return tomlWriter.addDependency(config, request.section(), request.coordinate(), request.version());
    }

    private void printAddSummary(ProjectConfig original, AddRequest request) {
        Map<String, String> dependencies = DependencyEditCommands.dependencies(original, request.section());
        String section = DependencyEditCommands.sectionName(request.section());
        String existing = dependencies.get(request.coordinate());
        String existingWorkspace = DependencyEditCommands.workspaceDependencies(original, request.section()).get(request.coordinate());
        String conflicting = DependencyEditCommands.conflictingDependencies(original, request.section()).get(request.coordinate());
        String conflictingWorkspace =
                DependencyEditCommands.conflictingWorkspaceDependencies(original, request.section()).get(request.coordinate());
        String existingVersionRef = DependencyEditCommands.versionRef(original, request.section(), request.coordinate());
        boolean existingManaged =
                DependencyEditCommands.managedDependencies(original, request.section()).contains(request.coordinate());
        boolean conflictingManaged =
                DependencyEditCommands.conflictingManagedDependencies(original, request.section()).contains(request.coordinate());
        if (request.managed()) {
            if (existingManaged) {
                spec.commandLine().getOut().println("Dependency " + request.coordinate()
                        + " already uses a platform-managed version in [" + section + "]");
            } else if (existing != null) {
                spec.commandLine().getOut().println("Updated dependency " + request.coordinate()
                        + " from " + existing + " to platform-managed version in [" + section + "]");
            } else if (existingWorkspace != null) {
                spec.commandLine().getOut().println("Updated dependency " + request.coordinate()
                        + " from workspace member " + existingWorkspace
                        + " to platform-managed version in [" + section + "]");
            } else if (conflicting != null || conflictingManaged || conflictingWorkspace != null) {
                spec.commandLine().getOut().println("Updated dependency " + request.coordinate()
                        + " from " + DependencyEditCommands.existingDescription(conflicting, conflictingManaged, conflictingWorkspace)
                        + " to platform-managed version in [" + section + "]");
            } else {
                spec.commandLine().getOut().println("Added dependency " + request.coordinate()
                        + " with a platform-managed version to [" + section + "]");
            }
            return;
        }
        if (request.versionRef() != null) {
            String version = original.versionAliases().get(request.versionRef());
            String versionRefDescription = "versionRef `" + request.versionRef() + "` = " + version;
            if (request.versionRef().equals(existingVersionRef)) {
                spec.commandLine().getOut().println("Dependency " + request.coordinate()
                        + " already uses " + versionRefDescription + " in [" + section + "]");
            } else if (existingVersionRef != null) {
                spec.commandLine().getOut().println("Updated dependency " + request.coordinate()
                        + " from versionRef `" + existingVersionRef + "` to " + versionRefDescription
                        + " in [" + section + "]");
            } else if (existingManaged) {
                spec.commandLine().getOut().println("Updated dependency " + request.coordinate()
                        + " from managed version to " + versionRefDescription + " in [" + section + "]");
            } else if (existing != null) {
                spec.commandLine().getOut().println("Updated dependency " + request.coordinate()
                        + " from " + existing + " to " + versionRefDescription + " in [" + section + "]");
            } else if (existingWorkspace != null) {
                spec.commandLine().getOut().println("Updated dependency " + request.coordinate()
                        + " from workspace member " + existingWorkspace
                        + " to " + versionRefDescription + " in [" + section + "]");
            } else if (conflicting != null || conflictingManaged || conflictingWorkspace != null) {
                spec.commandLine().getOut().println("Updated dependency " + request.coordinate()
                        + " from " + DependencyEditCommands.existingDescription(conflicting, conflictingManaged, conflictingWorkspace)
                        + " to " + versionRefDescription + " in [" + section + "]");
            } else {
                spec.commandLine().getOut().println("Added dependency " + request.coordinate()
                        + " with " + versionRefDescription + " to [" + section + "]");
            }
            return;
        }
        if (request.version().equals(existing)) {
            spec.commandLine().getOut().println("Dependency " + request.coordinate() + ":" + request.version()
                    + " already exists in [" + section + "]");
        } else if (existingManaged) {
            spec.commandLine().getOut().println("Updated dependency " + request.coordinate()
                    + " from managed version to " + request.version() + " in [" + section + "]");
        } else if (existing != null) {
            spec.commandLine().getOut().println("Updated dependency " + request.coordinate()
                    + " from " + existing + " to " + request.version() + " in [" + section + "]");
        } else if (existingWorkspace != null) {
            spec.commandLine().getOut().println("Updated dependency " + request.coordinate()
                    + " from workspace member " + existingWorkspace
                    + " to " + request.version() + " in [" + section + "]");
        } else if (conflicting != null || conflictingManaged || conflictingWorkspace != null) {
            spec.commandLine().getOut().println("Updated dependency " + request.coordinate()
                    + " from " + DependencyEditCommands.existingDescription(conflicting, conflictingManaged, conflictingWorkspace)
                    + " to " + request.version() + " in [" + section + "]");
        } else {
            spec.commandLine().getOut().println("Added dependency " + request.coordinate() + ":" + request.version()
                    + " to [" + section + "]");
        }
    }
}
