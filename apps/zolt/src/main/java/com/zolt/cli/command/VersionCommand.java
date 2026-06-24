package com.zolt.cli.command;

import com.zolt.cache.ArtifactCacheException;
import com.zolt.cli.CommandHumanOutput;
import com.zolt.cli.ZoltCli;
import com.zolt.cli.command.VersionAliasCommands.VersionAliasCommandException;
import com.zolt.project.ProjectConfig;
import com.zolt.resolve.ResolveException;
import com.zolt.resolve.ResolveService;
import com.zolt.toml.ZoltConfigException;
import com.zolt.toml.ZoltTomlParser;
import com.zolt.toml.ZoltTomlWriter;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

@Command(
        name = "version",
        description = "Print the Zolt version.",
        subcommands = {
                VersionCommand.SetCommand.class,
                VersionCommand.RemoveCommand.class
        })
public final class VersionCommand implements Runnable {
    @Spec
    private CommandSpec spec;

    @Override
    public void run() {
        spec.commandLine().getOut().println(ZoltCli.VERSION);
    }

    @Command(
            name = "set",
            description = "Set a version alias in zolt.toml and refresh zolt.lock.")
    public static final class SetCommand implements Runnable {
        private final ZoltTomlParser tomlParser;
        private final ZoltTomlWriter tomlWriter;
        private final ResolveService resolveService;

        @Parameters(index = "0", paramLabel = "ALIAS", description = "Version alias name.")
        private String alias;

        @Parameters(index = "1", paramLabel = "VERSION", description = "Literal version value.")
        private String version;

        @Option(names = "--no-resolve", description = "Update zolt.toml without refreshing zolt.lock.")
        private boolean noResolve;

        @Mixin
        private CommandProjectDirectory projectDirectory = new CommandProjectDirectory();

        @Option(names = "--cache-root", hidden = true)
        private Path cacheRoot = com.zolt.cache.LocalArtifactCache.defaultRoot();

        @Spec
        private CommandSpec spec;

        public SetCommand() {
            this(new ZoltTomlParser(), new ZoltTomlWriter(), CommandFrameworkServices.resolveService());
        }

        SetCommand(ZoltTomlParser tomlParser, ZoltTomlWriter tomlWriter, ResolveService resolveService) {
            this.tomlParser = tomlParser;
            this.tomlWriter = tomlWriter;
            this.resolveService = resolveService;
        }

        @Override
        public void run() {
            try {
                String normalizedAlias = VersionAliasCommands.validateAlias(alias);
                String normalizedVersion = VersionAliasCommands.validateValue(normalizedAlias, version);
                Path projectRoot = projectDirectory.path();
                Path configPath = projectRoot.resolve("zolt.toml");
                ProjectConfig config = tomlParser.parse(configPath);
                Map<String, String> aliases = new LinkedHashMap<>(config.versionAliases());
                String previous = aliases.put(normalizedAlias, normalizedVersion);
                ProjectConfig updated = config.withVersionAliases(aliases);
                tomlWriter.write(configPath, updated);
                printVersionAliasSummary(normalizedAlias, normalizedVersion, previous);
                if (noResolve) {
                    CommandHumanOutput.of(spec).line("Skipped resolve; run zolt resolve to refresh zolt.lock.");
                    return;
                }
                CommandResolveOutput.print(spec, resolveService.resolve(projectRoot, updated, cacheRoot));
            } catch (ArtifactCacheException
                    | ResolveException
                    | VersionAliasCommandException
                    | ZoltConfigException exception) {
                throw CommandFailures.user(spec, exception);
            }
        }

        private void printVersionAliasSummary(String alias, String version, String previous) {
            if (version.equals(previous)) {
                CommandHumanOutput.of(spec).detail(
                        "Version alias " + alias + " already equals " + version + " in [versions]");
            } else if (previous == null) {
                CommandHumanOutput.of(spec).success(
                        "Added version alias " + alias + " = " + version + " to [versions]");
            } else {
                CommandHumanOutput.of(spec).success(
                        "Updated version alias " + alias + " from " + previous + " to " + version + " in [versions]");
            }
        }
    }

    @Command(
            name = "remove",
            description = "Remove an unused version alias from zolt.toml and refresh zolt.lock.")
    public static final class RemoveCommand implements Runnable {
        private final ZoltTomlParser tomlParser;
        private final ZoltTomlWriter tomlWriter;
        private final ResolveService resolveService;

        @Parameters(index = "0", paramLabel = "ALIAS", description = "Version alias name.")
        private String alias;

        @Option(names = "--no-resolve", description = "Update zolt.toml without refreshing zolt.lock.")
        private boolean noResolve;

        @Mixin
        private CommandProjectDirectory projectDirectory = new CommandProjectDirectory();

        @Option(names = "--cache-root", hidden = true)
        private Path cacheRoot = com.zolt.cache.LocalArtifactCache.defaultRoot();

        @Spec
        private CommandSpec spec;

        public RemoveCommand() {
            this(new ZoltTomlParser(), new ZoltTomlWriter(), CommandFrameworkServices.resolveService());
        }

        RemoveCommand(ZoltTomlParser tomlParser, ZoltTomlWriter tomlWriter, ResolveService resolveService) {
            this.tomlParser = tomlParser;
            this.tomlWriter = tomlWriter;
            this.resolveService = resolveService;
        }

        @Override
        public void run() {
            try {
                String normalizedAlias = VersionAliasCommands.validateAlias(alias);
                Path projectRoot = projectDirectory.path();
                Path configPath = projectRoot.resolve("zolt.toml");
                ProjectConfig config = tomlParser.parse(configPath);
                Map<String, String> aliases = new LinkedHashMap<>(config.versionAliases());
                if (!aliases.containsKey(normalizedAlias)) {
                    throw new VersionAliasCommandException(
                            "Version alias `" + normalizedAlias + "` is not declared in [versions].");
                }
                List<String> references = VersionAliasCommands.references(config, normalizedAlias);
                if (!references.isEmpty()) {
                    throw new VersionAliasCommandException(
                            "Version alias `"
                                    + normalizedAlias
                                    + "` is still referenced by "
                                    + String.join(", ", references)
                                    + ". Remove or update those versionRef declarations before removing [versions]."
                                    + normalizedAlias
                                    + ".");
                }
                aliases.remove(normalizedAlias);
                ProjectConfig updated = config.withVersionAliases(aliases);
                tomlWriter.write(configPath, updated);
                CommandHumanOutput output = CommandHumanOutput.of(spec);
                output.success("Removed version alias " + normalizedAlias + " from [versions]");
                if (noResolve) {
                    output.line("Skipped resolve; run zolt resolve to refresh zolt.lock.");
                    return;
                }
                CommandResolveOutput.print(spec, resolveService.resolve(projectRoot, updated, cacheRoot));
            } catch (ArtifactCacheException
                    | ResolveException
                    | VersionAliasCommandException
                    | ZoltConfigException exception) {
                throw CommandFailures.user(spec, exception);
            }
        }
    }
}
