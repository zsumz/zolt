package com.zolt.cli.command;

import com.zolt.lockfile.LockfileReadException;
import com.zolt.project.ProjectConfig;
import com.zolt.resolve.ResolveService;
import com.zolt.workspace.Workspace;
import com.zolt.workspace.WorkspaceDiscoveryService;
import com.zolt.workspace.WorkspaceResolveService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

final class CommandLockfiles {
    private CommandLockfiles() {
    }

    static void requireFreshLockfile(
            Path workingDirectory,
            ProjectConfig config,
            Path cacheRoot,
            boolean offline) {
        Path lockfilePath = workingDirectory.resolve("zolt.lock");
        if (!Files.isRegularFile(lockfilePath) || !looksGeneratedLockfile(lockfilePath)) {
            return;
        }
        new ResolveService().resolve(workingDirectory, config, cacheRoot, true, offline);
    }

    static void requireFreshWorkspaceLockfile(Path workingDirectory, Path cacheRoot, boolean offline) {
        Optional<Workspace> workspace = new WorkspaceDiscoveryService().discover(workingDirectory.toAbsolutePath().normalize());
        if (workspace.isEmpty()) {
            return;
        }
        Path lockfilePath = workspace.orElseThrow().root().resolve("zolt.lock");
        if (!Files.isRegularFile(lockfilePath) || !looksGeneratedLockfile(lockfilePath)) {
            return;
        }
        new WorkspaceResolveService().resolve(workingDirectory, cacheRoot, true, offline);
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
