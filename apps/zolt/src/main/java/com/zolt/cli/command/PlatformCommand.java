package com.zolt.cli.command;

import com.zolt.cache.ArtifactCacheException;
import com.zolt.cache.LocalArtifactCache;
import com.zolt.cli.CommandHumanOutput;
import com.zolt.cli.command.DependencyEditCommands.PlatformCommandException;
import com.zolt.maven.Coordinate;
import com.zolt.maven.CoordinateParseException;
import com.zolt.maven.CoordinateParser;
import com.zolt.project.DependencyMetadata;
import com.zolt.project.ProjectConfig;
import com.zolt.project.VersionPolicy;
import com.zolt.resolve.ResolveException;
import com.zolt.resolve.ResolveService;
import com.zolt.toml.ZoltConfigException;
import com.zolt.toml.ZoltTomlParser;
import com.zolt.toml.ZoltTomlWriter;
import java.nio.file.Path;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

@Command(
        name = "platform",
        description = "Manage BOM/platform imports in zolt.toml.",
        subcommands = {
                PlatformCommand.AddCommand.class,
                PlatformCommand.RemoveCommand.class
        })
public final class PlatformCommand implements Runnable {
    @Spec
    private CommandSpec spec;

    @Override
    public void run() {
        spec.commandLine().usage(spec.commandLine().getOut());
    }

    @Command(
            name = "add",
            description = "Add a platform BOM import to zolt.toml and refresh zolt.lock.")
    public static final class AddCommand implements Runnable {
        private final CoordinateParser coordinateParser;
        private final ZoltTomlParser tomlParser;
        private final ZoltTomlWriter tomlWriter;
        private final ResolveService resolveService;

        @Parameters(index = "0", paramLabel = "GROUP:ARTIFACT[:VERSION]", description = "Platform BOM coordinate.")
        private String coordinate;

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
            this(CommandFrameworkServices.dependencyEditCommandServices());
        }

        private AddCommand(CommandDependencyEditServices services) {
            this(
                    services.coordinateParser(),
                    services.tomlParser(),
                    services.tomlWriter(),
                    services.resolveService());
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
                Coordinate parsed = coordinateParser.parse(coordinate);
                String platform = parsed.groupId() + ":" + parsed.artifactId();
                Path projectRoot = projectDirectory.path();
                Path configPath = projectRoot.resolve("zolt.toml");
                ProjectConfig config = tomlParser.parse(configPath);
                PlatformAddRequest request = addRequest(config, parsed, platform);
                ProjectConfig updated = request.versionRef() == null
                        ? tomlWriter.addPlatform(config, platform, request.version())
                        : tomlWriter.addVersionRefPlatform(
                                config,
                                platform,
                                request.versionRef(),
                                request.version());
                tomlWriter.write(configPath, updated);
                CommandHumanOutput output = CommandHumanOutput.of(spec);
                printAddSummary(output, config, platform, request);
                if (noResolve) {
                    output.line("Skipped resolve; run zolt resolve to refresh zolt.lock.");
                    return;
                }
                CommandResolveOutput.print(spec, resolveService.resolve(projectRoot, updated, cacheRoot));
            } catch (PlatformCommandException
                    | ArtifactCacheException
                    | CoordinateParseException
                    | ResolveException
                    | ZoltConfigException exception) {
                throw CommandFailures.user(spec, exception);
            }
        }

        private PlatformAddRequest addRequest(ProjectConfig config, Coordinate parsed, String platform) {
            if (versionRef != null && versionRef.isBlank()) {
                throw new PlatformCommandException(
                        "Version alias for --version-ref must be non-empty. Use `--version-ref <alias>`.");
            }
            if (versionRef != null && parsed.version().isPresent()) {
                throw new PlatformCommandException(
                        "Version-ref platform coordinate must not include a version. Use `--version-ref "
                                + versionRef
                                + " "
                                + platform
                                + "`.");
            }
            if (versionRef == null) {
                String version = parsed.version().orElseThrow(() -> new PlatformCommandException(
                        "Platform coordinate must include a version. Use `group:artifact:version` or `--version-ref <alias> group:artifact`."));
                DependencyEditCommands.validateCommandVersion(
                        VersionPolicy.Context.PLATFORM,
                        "platform",
                        version,
                        PlatformCommandException::new);
                return new PlatformAddRequest(version, null);
            }
            String version = config.versionAliases().get(versionRef);
            if (version == null) {
                throw new PlatformCommandException(
                        "Unknown versionRef `"
                                + versionRef
                                + "`. Add [versions]."
                                + versionRef
                                + " or use an explicit version.");
            }
            return new PlatformAddRequest(version, versionRef);
        }

        private void printAddSummary(
                CommandHumanOutput output,
                ProjectConfig original,
                String platform,
                PlatformAddRequest request) {
            String version = request.version();
            String existing = original.platforms().get(platform);
            String existingVersionRef = platformVersionRef(original, platform);
            if (request.versionRef() != null) {
                String versionRefDescription = "versionRef `" + request.versionRef() + "` = " + version;
                if (request.versionRef().equals(existingVersionRef)) {
                    output.detail("Platform " + platform + " already uses "
                            + versionRefDescription + " in [platforms]");
                } else if (existingVersionRef != null) {
                    output.success("Updated platform " + platform
                            + " from versionRef `" + existingVersionRef + "` to "
                            + versionRefDescription + " in [platforms]");
                } else if (existing != null) {
                    output.success("Updated platform " + platform
                            + " from " + existing + " to " + versionRefDescription + " in [platforms]");
                } else {
                    output.success("Added platform " + platform
                            + " with " + versionRefDescription + " to [platforms]");
                }
                return;
            }
            if (existingVersionRef != null) {
                output.success("Updated platform " + platform
                        + " from versionRef `" + existingVersionRef + "` to " + version + " in [platforms]");
            } else if (version.equals(existing)) {
                output.detail("Platform " + platform + ":" + version
                        + " already exists in [platforms]");
            } else if (existing != null) {
                output.success("Updated platform " + platform
                        + " from " + existing + " to " + version + " in [platforms]");
            } else {
                output.success("Added platform " + platform + ":" + version
                        + " to [platforms]");
            }
        }

        private static String platformVersionRef(ProjectConfig config, String platform) {
            DependencyMetadata metadata =
                    config.dependencyMetadata().get(DependencyMetadata.key("platforms", platform));
            return metadata == null ? null : metadata.versionRef();
        }

        private record PlatformAddRequest(String version, String versionRef) {}
    }

    @Command(
            name = "remove",
            description = "Remove a platform BOM import and refresh zolt.lock.")
    public static final class RemoveCommand implements Runnable {
        private final CoordinateParser coordinateParser;
        private final ZoltTomlParser tomlParser;
        private final ZoltTomlWriter tomlWriter;
        private final ResolveService resolveService;

        @Parameters(index = "0", paramLabel = "GROUP:ARTIFACT", description = "Platform BOM coordinate.")
        private String coordinate;

        @Mixin
        private CommandProjectDirectory projectDirectory = new CommandProjectDirectory();

        @Option(names = "--cache-root", hidden = true)
        private Path cacheRoot = LocalArtifactCache.defaultRoot();

        @Spec
        private CommandSpec spec;

        public RemoveCommand() {
            this(CommandFrameworkServices.dependencyEditCommandServices());
        }

        private RemoveCommand(CommandDependencyEditServices services) {
            this(
                    services.coordinateParser(),
                    services.tomlParser(),
                    services.tomlWriter(),
                    services.resolveService());
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
                Coordinate parsed = coordinateParser.parse(coordinate);
                if (parsed.version().isPresent()) {
                    throw new PlatformCommandException(
                            "Platform remove coordinate must not include a version. Use `group:artifact`.");
                }
                String platform = parsed.groupId() + ":" + parsed.artifactId();
                Path projectRoot = projectDirectory.path();
                Path configPath = projectRoot.resolve("zolt.toml");
                ProjectConfig config = tomlParser.parse(configPath);
                CommandHumanOutput output = CommandHumanOutput.of(spec);
                if (!config.platforms().containsKey(platform)) {
                    output.detail("Platform " + platform + " is not present in [platforms]; nothing to remove.");
                    return;
                }
                ProjectConfig updated = tomlWriter.removePlatform(config, platform);
                tomlWriter.write(configPath, updated);
                output.success("Removed platform " + platform + " from [platforms]");
                CommandResolveOutput.print(spec, resolveService.resolve(projectRoot, updated, cacheRoot));
            } catch (PlatformCommandException
                    | ArtifactCacheException
                    | CoordinateParseException
                    | ResolveException
                    | ZoltConfigException exception) {
                throw CommandFailures.user(spec, exception);
            }
        }
    }
}
