package com.zolt.cli.command.task;

import com.zolt.toml.ZoltConfigException;
import com.zolt.workspace.WorkspaceConfigException;
import com.zolt.workspace.WorkspaceConfigParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import picocli.CommandLine;
import picocli.CommandLine.Model.CommandSpec;

final class CommandConfigRoots {
    private final WorkspaceConfigParser workspaceConfigParser;

    CommandConfigRoots() {
        this(new WorkspaceConfigParser());
    }

    CommandConfigRoots(WorkspaceConfigParser workspaceConfigParser) {
        this.workspaceConfigParser = workspaceConfigParser;
    }

    Path discoverConfig(Path startDirectory) {
        Path current = startDirectory.toAbsolutePath().normalize();
        if (Files.isRegularFile(current)) {
            current = current.getParent();
        }

        Path nearestZoltToml = null;
        while (current != null) {
            Path rootConfig = current.resolve(WorkspaceConfigParser.ROOT_CONFIG_FILE).normalize();
            if (Files.isRegularFile(rootConfig)) {
                if (hasWorkspaceSection(rootConfig)) {
                    return rootConfig;
                }
                if (nearestZoltToml == null) {
                    nearestZoltToml = rootConfig;
                }
            }

            Path legacyWorkspace = current.resolve(WorkspaceConfigParser.WORKSPACE_FILE).normalize();
            if (Files.isRegularFile(legacyWorkspace)) {
                if (Files.isRegularFile(rootConfig)) {
                    return rootConfig;
                }
                throw new ZoltConfigException(
                        "Could not find root zolt.toml command config at "
                                + rootConfig
                                + ". Add zolt.toml with [commands.tasks] or [commands.aliases].");
            }
            current = current.getParent();
        }

        if (nearestZoltToml != null) {
            return nearestZoltToml;
        }
        throw new ZoltConfigException(
                "Could not find zolt.toml command config. Run from a project or workspace directory, or add zolt.toml with [commands.tasks].");
    }

    static Set<String> builtInCommandNames(CommandSpec spec) {
        CommandLine root = spec.commandLine();
        while (root.getParent() != null) {
            root = root.getParent();
        }
        return new LinkedHashSet<>(root.getSubcommands().keySet());
    }

    private boolean hasWorkspaceSection(Path rootConfig) {
        try {
            return workspaceConfigParser.hasWorkspaceSection(rootConfig);
        } catch (WorkspaceConfigException exception) {
            throw new ZoltConfigException(exception.getMessage());
        }
    }
}
