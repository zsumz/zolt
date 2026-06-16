package com.zolt.workspace;

import com.zolt.lockfile.LockfileFreshnessSummary;
import com.zolt.lockfile.LockfileReadException;
import com.zolt.lockfile.ZoltLockfile;
import com.zolt.lockfile.ZoltLockfileReader;
import com.zolt.lockfile.ZoltLockfileWriter;
import com.zolt.project.ProjectConfig;
import com.zolt.dependency.PackageId;
import com.zolt.resolve.ResolveException;
import com.zolt.resolve.ResolveMetrics;
import com.zolt.resolve.ResolveOutput;
import com.zolt.resolve.ResolveResult;
import com.zolt.resolve.ResolveService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class WorkspaceResolveService {
    private final WorkspaceDiscoveryService workspaceDiscoveryService;
    private final ResolveService resolveService;
    private final ZoltLockfileWriter lockfileWriter;
    private final WorkspacePolicyMerger policyMerger;
    private final WorkspaceLockfileAggregator lockfileAggregator;

    public WorkspaceResolveService() {
        this(new WorkspaceDiscoveryService(), new ResolveService(), new ZoltLockfileWriter(), new WorkspacePolicyMerger());
    }

    public WorkspaceResolveService(ResolveService resolveService) {
        this(new WorkspaceDiscoveryService(), resolveService, new ZoltLockfileWriter(), new WorkspacePolicyMerger());
    }

    WorkspaceResolveService(
            WorkspaceDiscoveryService workspaceDiscoveryService,
            ResolveService resolveService,
            ZoltLockfileWriter lockfileWriter) {
        this(workspaceDiscoveryService, resolveService, lockfileWriter, new WorkspacePolicyMerger());
    }

    WorkspaceResolveService(
            WorkspaceDiscoveryService workspaceDiscoveryService,
            ResolveService resolveService,
            ZoltLockfileWriter lockfileWriter,
            WorkspacePolicyMerger policyMerger) {
        this.workspaceDiscoveryService = workspaceDiscoveryService;
        this.resolveService = resolveService;
        this.lockfileWriter = lockfileWriter;
        this.policyMerger = policyMerger;
        this.lockfileAggregator = new WorkspaceLockfileAggregator();
    }

    public ResolveResult resolve(Path startDirectory, Path cacheRoot, boolean locked, boolean offline) {
        Path start = startDirectory.toAbsolutePath().normalize();
        Workspace workspace = workspaceDiscoveryService.discover(start).orElseThrow(() -> new ResolveException(
                "Could not find zolt-workspace.toml. Run `zolt resolve --workspace` from a workspace directory or create zolt-workspace.toml."));
        Path lockfilePath = workspace.root().resolve("zolt.lock");
        if (locked && !Files.isRegularFile(lockfilePath)) {
            throw new ResolveException(
                    "Locked workspace resolve requires zolt.lock at "
                            + lockfilePath
                            + ". Run `zolt resolve --workspace` to create it, then retry `zolt resolve --workspace --locked`.");
        }

        Map<String, WorkspaceMember> membersByPath = membersByPath(workspace);
        List<WorkspaceMemberResolveOutput> memberOutputs = new ArrayList<>();
        int downloadCount = 0;
        ResolveMetrics metrics = ResolveMetrics.empty();
        for (String memberPath : workspace.buildOrder()) {
            WorkspaceMember member = membersByPath.get(memberPath);
            ResolveOutput output = resolveService.resolveLockfile(
                    policyMerger.merge(workspace, member),
                    cacheRoot,
                    offline);
            memberOutputs.add(new WorkspaceMemberResolveOutput(
                    member.path(),
                    output.lockfile(),
                    exportedExternalPackageIds(member.config())));
            downloadCount += output.downloadCount();
            metrics = metrics.plus(output.metrics());
        }

        ZoltLockfile lockfile = lockfileAggregator.aggregate(workspace, memberOutputs);
        if (locked) {
            long started = System.nanoTime();
            verifyLocked(lockfilePath, lockfile);
            metrics = metrics.withLockfileVerificationNanos(elapsedSince(started));
        } else {
            long started = System.nanoTime();
            lockfileWriter.write(lockfilePath, lockfile);
            metrics = metrics.withLockfileWriteNanos(elapsedSince(started));
        }
        return new ResolveResult(
                lockfile.packages().size(),
                downloadCount,
                lockfile.conflicts().size(),
                lockfilePath,
                metrics);
    }

    private static long elapsedSince(long started) {
        return Math.max(0L, System.nanoTime() - started);
    }

    private void verifyLocked(Path lockfilePath, ZoltLockfile candidate) {
        String existing;
        try {
            existing = Files.readString(lockfilePath);
        } catch (IOException exception) {
            throw new ResolveException(
                    "Could not read zolt.lock at "
                            + lockfilePath
                            + " for locked workspace resolve. Check that the file exists and is readable.",
                    exception);
        }

        String expected = lockfileWriter.write(candidate);
        if (!existing.equals(expected)) {
            String changedInputs = changedInputs(existing, candidate);
            throw new ResolveException(
                    "Workspace zolt.lock is out of date."
                            + changedInputs
                            + " Run `zolt resolve --workspace` to refresh it, then retry `zolt resolve --workspace --locked`.");
        }
    }

    private static String changedInputs(String existing, ZoltLockfile candidate) {
        try {
            return LockfileFreshnessSummary.changedInputs(new ZoltLockfileReader().read(existing), candidate);
        } catch (LockfileReadException exception) {
            return "";
        }
    }

    private static Set<PackageId> exportedExternalPackageIds(ProjectConfig config) {
        Set<PackageId> packageIds = new LinkedHashSet<>();
        config.apiDependencies().keySet().forEach(coordinate -> packageIds.add(packageId(coordinate)));
        config.managedApiDependencies().forEach(coordinate -> packageIds.add(packageId(coordinate)));
        return Set.copyOf(packageIds);
    }

    private static PackageId packageId(String coordinate) {
        String[] parts = coordinate.split(":", -1);
        return new PackageId(parts[0], parts[1]);
    }

    private static Map<String, WorkspaceMember> membersByPath(Workspace workspace) {
        Map<String, WorkspaceMember> members = new LinkedHashMap<>();
        for (WorkspaceMember member : workspace.members()) {
            members.put(member.path(), member);
        }
        return members;
    }
}
