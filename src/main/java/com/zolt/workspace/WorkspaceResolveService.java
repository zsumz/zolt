package com.zolt.workspace;

import com.zolt.lockfile.LockConflict;
import com.zolt.lockfile.LockPackage;
import com.zolt.lockfile.ZoltLockfile;
import com.zolt.lockfile.ZoltLockfileWriter;
import com.zolt.project.ProjectConfig;
import com.zolt.resolve.DependencyScope;
import com.zolt.resolve.PackageId;
import com.zolt.resolve.ResolveException;
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
import java.util.Optional;
import java.util.Set;

public final class WorkspaceResolveService {
    private final WorkspaceDiscoveryService workspaceDiscoveryService;
    private final ResolveService resolveService;
    private final ZoltLockfileWriter lockfileWriter;

    public WorkspaceResolveService() {
        this(new WorkspaceDiscoveryService(), new ResolveService(), new ZoltLockfileWriter());
    }

    WorkspaceResolveService(
            WorkspaceDiscoveryService workspaceDiscoveryService,
            ResolveService resolveService,
            ZoltLockfileWriter lockfileWriter) {
        this.workspaceDiscoveryService = workspaceDiscoveryService;
        this.resolveService = resolveService;
        this.lockfileWriter = lockfileWriter;
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
        List<MemberResolveOutput> memberOutputs = new ArrayList<>();
        int downloadCount = 0;
        for (String memberPath : workspace.buildOrder()) {
            WorkspaceMember member = membersByPath.get(memberPath);
            ResolveOutput output = resolveService.resolveLockfile(
                    mergeWorkspacePolicy(workspace, member),
                    cacheRoot,
                    offline);
            memberOutputs.add(new MemberResolveOutput(
                    member.path(),
                    output.lockfile(),
                    exportedExternalPackageIds(member.config())));
            downloadCount += output.downloadCount();
        }

        ZoltLockfile lockfile = aggregate(workspace, memberOutputs);
        if (locked) {
            verifyLocked(lockfilePath, lockfile);
        } else {
            lockfileWriter.write(lockfilePath, lockfile);
        }
        return new ResolveResult(
                lockfile.packages().size(),
                downloadCount,
                lockfile.conflicts().size(),
                lockfilePath);
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
            throw new ResolveException(
                    "Workspace zolt.lock is out of date. Run `zolt resolve --workspace` to refresh it, then retry `zolt resolve --workspace --locked`.");
        }
    }

    private static ProjectConfig mergeWorkspacePolicy(Workspace workspace, WorkspaceMember member) {
        ProjectConfig config = member.config();
        return new ProjectConfig(
                config.project(),
                mergedPolicy(
                        "repository",
                        workspace,
                        member,
                        workspace.config().repositories(),
                        config.repositories()),
                mergedPolicy(
                        "platform",
                        workspace,
                        member,
                        workspace.config().platforms(),
                        config.platforms()),
                config.apiDependencies(),
                config.managedApiDependencies(),
                config.workspaceApiDependencies(),
                config.dependencies(),
                config.managedDependencies(),
                config.workspaceDependencies(),
                config.testDependencies(),
                config.managedTestDependencies(),
                config.workspaceTestDependencies(),
                config.annotationProcessors(),
                config.managedAnnotationProcessors(),
                config.testAnnotationProcessors(),
                config.managedTestAnnotationProcessors(),
                config.build(),
                config.nativeSettings(),
                config.compilerSettings());
    }

    private static Map<String, String> mergedPolicy(
            String kind,
            Workspace workspace,
            WorkspaceMember member,
            Map<String, String> workspaceValues,
            Map<String, String> memberValues) {
        Map<String, String> merged = new LinkedHashMap<>(workspaceValues);
        for (Map.Entry<String, String> entry : memberValues.entrySet()) {
            String existing = merged.putIfAbsent(entry.getKey(), entry.getValue());
            if (existing != null && !existing.equals(entry.getValue())) {
                throw new ResolveException(
                        "Workspace "
                                + kind
                                + " `"
                                + entry.getKey()
                                + "` has value `"
                                + existing
                                + "` in "
                                + workspace.configPath()
                                + " but member `"
                                + member.path()
                                + "` declares `"
                                + entry.getValue()
                                + "`. Make the values match or remove the member override.");
            }
        }
        return merged;
    }

    private static ZoltLockfile aggregate(Workspace workspace, List<MemberResolveOutput> memberOutputs) {
        Map<String, LockPackage> packages = new LinkedHashMap<>();
        Map<PackageId, VersionOwner> selectedVersions = new LinkedHashMap<>();
        Map<String, LockConflict> conflicts = new LinkedHashMap<>();
        for (LockPackage lockPackage : workspacePackages(workspace)) {
            selectedVersions.putIfAbsent(lockPackage.packageId(), new VersionOwner(lockPackage.version(), lockPackage.workspace().orElse("<workspace>")));
            String key = packageKey(lockPackage);
            LockPackage existingPackage = packages.get(key);
            packages.put(key, existingPackage == null ? lockPackage : merge(existingPackage, lockPackage));
        }
        for (MemberResolveOutput memberOutput : memberOutputs) {
            for (LockPackage lockPackage : memberOutput.lockfile().packages()) {
                VersionOwner existingVersion = selectedVersions.putIfAbsent(
                        lockPackage.packageId(),
                        new VersionOwner(lockPackage.version(), memberOutput.member()));
                if (existingVersion != null && !existingVersion.version().equals(lockPackage.version())) {
                    throw new ResolveException(
                            "Workspace dependency version conflict for "
                                    + lockPackage.packageId()
                                    + ": member `"
                                    + existingVersion.member()
                                    + "` selected "
                                    + existingVersion.version()
                                    + " but member `"
                                    + memberOutput.member()
                                    + "` selected "
                                    + lockPackage.version()
                                    + ". Align dependency versions before running workspace resolve.");
                }

                LockPackage memberPackage = withMember(lockPackage, memberOutput.member(), memberOutput.exportedPackageIds());
                String key = packageKey(memberPackage);
                LockPackage existingPackage = packages.get(key);
                packages.put(key, existingPackage == null ? memberPackage : merge(existingPackage, memberPackage));
            }
            for (LockConflict conflict : memberOutput.lockfile().conflicts()) {
                conflicts.putIfAbsent(conflictKey(conflict), conflict);
            }
        }
        return new ZoltLockfile(
                ZoltLockfile.CURRENT_VERSION,
                List.copyOf(packages.values()),
                List.copyOf(conflicts.values()));
    }

    private static List<LockPackage> workspacePackages(Workspace workspace) {
        Map<String, WorkspaceMember> membersByPath = membersByPath(workspace);
        List<LockPackage> packages = new ArrayList<>();
        for (WorkspaceProjectEdge edge : workspace.edges()) {
            WorkspaceMember target = membersByPath.get(edge.to());
            packages.add(new LockPackage(
                    packageId(edge.coordinate()),
                    target.config().project().version(),
                    "workspace",
                    scope(edge.scope()),
                    true,
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.of(edge.to()),
                    Optional.of(target.config().build().output()),
                    List.of(),
                    List.of(edge.from()),
                    edge.exported() ? List.of(edge.from()) : List.of()));
        }
        return packages;
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

    private static DependencyScope scope(String value) {
        return switch (value) {
            case "compile" -> DependencyScope.COMPILE;
            case "test" -> DependencyScope.TEST;
            default -> throw new ResolveException("Unsupported workspace dependency scope `" + value + "`.");
        };
    }

    private static LockPackage merge(LockPackage left, LockPackage right) {
        Set<String> dependencies = new LinkedHashSet<>(left.dependencies());
        dependencies.addAll(right.dependencies());
        Set<String> members = new LinkedHashSet<>(left.members());
        members.addAll(right.members());
        Set<String> exportedBy = new LinkedHashSet<>(left.exportedBy());
        exportedBy.addAll(right.exportedBy());
        return new LockPackage(
                left.packageId(),
                left.version(),
                left.source(),
                left.scope(),
                left.direct() || right.direct(),
                firstPresent(left.jar(), right.jar()),
                firstPresent(left.pom(), right.pom()),
                firstPresent(left.jarSha256(), right.jarSha256()),
                firstPresent(left.pomSha256(), right.pomSha256()),
                firstPresent(left.workspace(), right.workspace()),
                firstPresent(left.workspaceOutput(), right.workspaceOutput()),
                List.copyOf(dependencies),
                List.copyOf(members),
                List.copyOf(exportedBy));
    }

    private static LockPackage withMember(LockPackage lockPackage, String member, Set<PackageId> exportedPackageIds) {
        Set<String> members = new LinkedHashSet<>(lockPackage.members());
        members.add(member);
        Set<String> exportedBy = new LinkedHashSet<>(lockPackage.exportedBy());
        if (exportedPackageIds.contains(lockPackage.packageId())) {
            exportedBy.add(member);
        }
        return new LockPackage(
                lockPackage.packageId(),
                lockPackage.version(),
                lockPackage.source(),
                lockPackage.scope(),
                lockPackage.direct(),
                lockPackage.jar(),
                lockPackage.pom(),
                lockPackage.jarSha256(),
                lockPackage.pomSha256(),
                lockPackage.workspace(),
                lockPackage.workspaceOutput(),
                lockPackage.dependencies(),
                List.copyOf(members),
                List.copyOf(exportedBy));
    }

    private static Optional<String> firstPresent(Optional<String> left, Optional<String> right) {
        return left.isPresent() ? left : right;
    }

    private static String packageKey(LockPackage lockPackage) {
        return lockPackage.packageId()
                + ":"
                + lockPackage.version()
                + ":"
                + lockPackage.source()
                + ":"
                + lockPackage.scope().lockfileName();
    }

    private static String conflictKey(LockConflict conflict) {
        return conflict.packageId() + ":" + conflict.selectedVersion() + ":" + conflict.reason();
    }

    private static Map<String, WorkspaceMember> membersByPath(Workspace workspace) {
        Map<String, WorkspaceMember> members = new LinkedHashMap<>();
        for (WorkspaceMember member : workspace.members()) {
            members.put(member.path(), member);
        }
        return members;
    }

    private record MemberResolveOutput(
            String member,
            ZoltLockfile lockfile,
            Set<PackageId> exportedPackageIds) {
    }

    private record VersionOwner(
            String version,
            String member) {
    }
}
