package com.zolt.cli.command;

import com.zolt.config.RepositoryOverlayConfig;
import com.zolt.config.RepositoryOverlayConfigSource;
import com.zolt.config.UserGlobalConfig;
import com.zolt.config.UserGlobalConfigException;
import com.zolt.config.UserGlobalConfigParser;
import java.nio.file.Files;
import java.nio.file.Path;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

@Command(
        name = "config",
        description = "Inspect user-local Zolt config diagnostics.",
        subcommands = {ConfigCommand.ShowCommand.class})
public final class ConfigCommand implements Runnable {
    @Spec
    private CommandSpec spec;

    @Override
    public void run() {
        spec.commandLine().usage(spec.commandLine().getOut());
    }

    @Command(name = "show", description = "Show effective user global config settings.")
    public static final class ShowCommand implements Runnable {
        private final UserGlobalConfigParser parser;

        @Option(names = "--config", description = "User global config path. Defaults to ~/.zolt/config.toml.")
        private Path configPath = defaultConfigPath();

        @Spec
        private CommandSpec spec;

        public ShowCommand() {
            this(new UserGlobalConfigParser());
        }

        ShowCommand(UserGlobalConfigParser parser) {
            this.parser = parser;
        }

        @Override
        public void run() {
            try {
                Path normalizedConfigPath = configPath.toAbsolutePath().normalize();
                boolean configExists = Files.exists(normalizedConfigPath);
                UserGlobalConfig config = parser.read(normalizedConfigPath);
                print(config, configExists);
            } catch (UserGlobalConfigException exception) {
                throw CommandFailures.user(spec, exception);
            }
        }

        private void print(UserGlobalConfig config, boolean configExists) {
            String configPathSource = isDefaultConfigPath(config.configPath()) ? "built-in default" : "flag";
            spec.commandLine().getOut().println("User global config: " + config.configPath());
            spec.commandLine().getOut().println("config path source: " + configPathSource);
            spec.commandLine().getOut().println("config file: " + (configExists ? "present" : "missing"));
            spec.commandLine().getOut().println("schema version: "
                    + config.version()
                    + " (source: "
                    + config.sources().version()
                    + ")");
            spec.commandLine().getOut().println("machine preferences only: yes");
            spec.commandLine().getOut().println("project semantics source: committed zolt.toml, zolt-workspace.toml, zolt.lock, env references, and command flags");
            spec.commandLine().getOut().println("cache.root: "
                    + config.cacheRoot()
                    + " (source: "
                    + config.sources().cacheRoot()
                    + ")");
            spec.commandLine().getOut().println("repository.downloadConcurrency: "
                    + config.repository().downloadConcurrency()
                    + " (source: "
                    + config.sources().repositoryDownloadConcurrency()
                    + ")");
            spec.commandLine().getOut().println("repository.executionLane: "
                    + config.repository().executionLane()
                    + " (source: "
                    + config.sources().repositoryExecutionLane()
                    + ")");
            for (RepositoryOverlayConfig overlay : config.repositoryOverlays().values()) {
                RepositoryOverlayConfigSource source = config.sources()
                        .repositoryOverlays()
                        .getOrDefault(overlay.id(), RepositoryOverlayConfigSource.defaults());
                spec.commandLine().getOut().println("repositoryOverlays."
                        + overlay.id()
                        + ".kind: "
                        + overlay.kind()
                        + " (source: "
                        + source.kind()
                        + ")");
                spec.commandLine().getOut().println("repositoryOverlays."
                        + overlay.id()
                        + ".enabled: "
                        + overlay.enabled()
                        + " (source: "
                        + source.enabled()
                        + ")");
            }
            spec.commandLine().getOut().println("ui.color: "
                    + config.ui().color()
                    + " (source: "
                    + config.sources().uiColor()
                    + ")");
            spec.commandLine().getOut().println("ui.progress: "
                    + config.ui().progress()
                    + " (source: "
                    + config.sources().uiProgress()
                    + ")");
            spec.commandLine().getOut().println("local overlay CI policy: reject with --no-local-overlays or zolt check --context ci");
            spec.commandLine().getOut().println("redaction: secret values are not read from user global config; repository credentials stay in env references from committed project config");
        }

        private static Path defaultConfigPath() {
            return Path.of(System.getProperty("user.home"), ".zolt", "config.toml").toAbsolutePath().normalize();
        }

        private static boolean isDefaultConfigPath(Path path) {
            return path.toAbsolutePath().normalize().equals(defaultConfigPath());
        }
    }
}
