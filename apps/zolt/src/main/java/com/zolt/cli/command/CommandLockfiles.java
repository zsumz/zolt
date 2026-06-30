package com.zolt.cli.command;

import com.zolt.lockfile.toml.LockfileReadException;
import com.zolt.project.ProjectConfig;
import com.zolt.resolve.ResolveService;
import com.zolt.cli.command.CommandServiceBundles.CommandResolveServices;
import com.zolt.workspace.service.Workspace;
import com.zolt.workspace.resolve.WorkspaceResolveService;
import com.zolt.workspace.discovery.WorkspaceDiscoveryService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public final class CommandLockfiles {
    private final ResolveService resolveService;
    private final WorkspaceDiscoveryService workspaceDiscoveryService;
    private final WorkspaceResolveService workspaceResolveService;

    public CommandLockfiles() {
        this(CommandFrameworkServices.resolveCommandServices());
    }

    private CommandLockfiles(CommandResolveServices services) {
        this(
                services.resolveService(),
                new WorkspaceDiscoveryService(),
                services.workspaceResolveService());
    }

    public CommandLockfiles(
            ResolveService resolveService,
            WorkspaceDiscoveryService workspaceDiscoveryService,
            WorkspaceResolveService workspaceResolveService) {
        this.resolveService = resolveService;
        this.workspaceDiscoveryService = workspaceDiscoveryService;
        this.workspaceResolveService = workspaceResolveService;
    }

    public void requireFreshLockfile(
            Path workingDirectory,
            ProjectConfig config,
            Path cacheRoot,
            boolean offline) {
        Path lockfilePath = workingDirectory.resolve("zolt.lock");
        if (!Files.isRegularFile(lockfilePath) || !looksGeneratedLockfile(lockfilePath)) {
            return;
        }
        resolveService.resolve(workingDirectory, config, cacheRoot, true, offline);
    }

    public void refreshExistingLockfile(
            Path workingDirectory,
            ProjectConfig config,
            Path cacheRoot,
            boolean offline) {
        Path lockfilePath = workingDirectory.resolve("zolt.lock");
        if (!Files.isRegularFile(lockfilePath) || !looksGeneratedLockfile(lockfilePath)) {
            return;
        }
        resolveService.resolve(workingDirectory, config, cacheRoot, false, offline);
    }

    public void requireFreshWorkspaceLockfile(Path workingDirectory, Path cacheRoot, boolean offline) {
        Optional<Workspace> workspace = workspaceDiscoveryService.discover(workingDirectory.toAbsolutePath().normalize());
        if (workspace.isEmpty()) {
            return;
        }
        Path lockfilePath = workspace.orElseThrow().root().resolve("zolt.lock");
        if (!Files.isRegularFile(lockfilePath) || !looksGeneratedLockfile(lockfilePath)) {
            return;
        }
        workspaceResolveService.resolve(workingDirectory, cacheRoot, true, offline);
    }

    private static boolean looksGeneratedLockfile(Path lockfilePath) {
        try {
            String content = Files.readString(lockfilePath);
            return content.contains("Sha256 = ")
                    || content.contains("aliasFingerprint = ")
                    || content.contains("projectResolutionFingerprint = ");
        } catch (IOException exception) {
            throw new LockfileReadException(
                    "Could not read zolt.lock at "
                            + lockfilePath
                            + " while checking lockfile freshness. Check that the file exists and is readable.",
                    exception);
        }
    }
}
