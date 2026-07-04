package sh.zolt.workspace.service;

import sh.zolt.build.BuildService;
import sh.zolt.build.JavacException;
import sh.zolt.classpath.ClasspathSet;
import sh.zolt.classpath.ResolvedClasspathPackage;
import sh.zolt.doctor.JdkChecker;
import sh.zolt.doctor.JdkDetector;
import sh.zolt.lockfile.ZoltLockfile;
import sh.zolt.lockfile.toml.ZoltLockfileReader;
import sh.zolt.resolve.ResolveException;
import sh.zolt.resolve.ResolveResult;
import sh.zolt.resolve.ResolveService;
import sh.zolt.workspace.discovery.WorkspaceDiscoveryService;
import sh.zolt.workspace.resolve.WorkspaceResolveService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class WorkspaceBuildService {
    private final WorkspaceDiscoveryService workspaceDiscoveryService;
    private final WorkspaceResolveService workspaceResolveService;
    private final ZoltLockfileReader lockfileReader;
    private final WorkspaceClasspathService workspaceClasspathService;
    private final BuildService buildService;
    private final WorkspaceMemberSelector memberSelector;

    public WorkspaceBuildService() {
        this(new JdkDetector());
    }

    public WorkspaceBuildService(ResolveService resolveService) {
        this(new JdkDetector(), resolveService);
    }

    WorkspaceBuildService(JdkChecker jdkDetector) {
        this(jdkDetector, new ResolveService());
    }

    public WorkspaceBuildService(JdkChecker jdkDetector, ResolveService resolveService) {
        this(
                new WorkspaceDiscoveryService(),
                new WorkspaceResolveService(resolveService),
                new ZoltLockfileReader(),
                new WorkspaceClasspathService(),
                new BuildService(jdkDetector, resolveService),
                new WorkspaceMemberSelector());
    }

    WorkspaceBuildService(
            WorkspaceDiscoveryService workspaceDiscoveryService,
            WorkspaceResolveService workspaceResolveService,
            ZoltLockfileReader lockfileReader,
            WorkspaceClasspathService workspaceClasspathService,
            BuildService buildService,
            WorkspaceMemberSelector memberSelector) {
        this.workspaceDiscoveryService = workspaceDiscoveryService;
        this.workspaceResolveService = workspaceResolveService;
        this.lockfileReader = lockfileReader;
        this.workspaceClasspathService = workspaceClasspathService;
        this.buildService = buildService;
        this.memberSelector = memberSelector;
    }

    public WorkspaceBuildResult build(Path startDirectory, Path cacheRoot, boolean offline) {
        return build(startDirectory, cacheRoot, offline, WorkspaceSelectionRequest.defaults());
    }

    public WorkspaceBuildResult build(
            Path startDirectory,
            Path cacheRoot,
            boolean offline,
            WorkspaceSelectionRequest selectionRequest) {
        return build(planBuild(startDirectory, cacheRoot, offline, selectionRequest), cacheRoot);
    }

    public WorkspaceBuildPlan planBuild(
            Path startDirectory,
            Path cacheRoot,
            boolean offline,
            WorkspaceSelectionRequest selectionRequest) {
        Path start = startDirectory.toAbsolutePath().normalize();
        Workspace workspace = workspaceDiscoveryService.discover(start).orElseThrow(() -> ResolveException.actionable(
                "Could not find workspace config.",
                "Run `zolt build --workspace` from a workspace directory or add zolt.toml with [workspace]."));
        WorkspaceSelection selection = memberSelector.select(workspace, selectionRequest);
        Path lockfilePath = workspace.root().resolve("zolt.lock");
        Optional<ResolveResult> resolveResult = Optional.empty();
        if (!Files.isRegularFile(lockfilePath)) {
            resolveResult = Optional.of(workspaceResolveService.resolve(
                    start,
                    cacheRoot,
                    false,
                    offline,
                    "zolt build --workspace"));
        }

        ZoltLockfile lockfile = lockfileReader.read(lockfilePath);
        return new WorkspaceBuildPlan(workspace, selection, resolveResult, lockfile);
    }

    public WorkspaceBuildResult build(WorkspaceBuildPlan plan, Path cacheRoot) {
        return build(
                plan,
                cacheRoot,
                new LinkedHashSet<>(plan.selection().includedMembers()));
    }

    WorkspaceBuildResult build(
            WorkspaceBuildPlan plan,
            Path cacheRoot,
            Set<String> fullClasspathMembers) {
        Workspace workspace = plan.workspace();
        WorkspaceSelection selection = plan.selection();
        ZoltLockfile lockfile = plan.lockfile();
        Map<String, WorkspaceMember> membersByPath = membersByPath(workspace);
        Map<String, ClasspathSet> classpathsByMember = workspaceClasspathService.classpathsForMembers(
                workspace,
                lockfile,
                cacheRoot,
                selection.includedMembers(),
                fullClasspathMembers);
        Map<String, List<ResolvedClasspathPackage>> classpathPackagesByMember =
                workspaceClasspathService.classpathPackagesForMembers(
                        workspace,
                        lockfile,
                        cacheRoot,
                        selection.includedMembers());
        List<WorkspaceBuildResult.MemberBuildResult> results = new ArrayList<>();
        for (String memberPath : selection.includedMembers()) {
            WorkspaceMember member = membersByPath.get(memberPath);
            ClasspathSet classpaths = classpathsByMember.get(member.path());
            try {
                results.add(new WorkspaceBuildResult.MemberBuildResult(
                        member.path(),
                        buildService.build(member.directory(), member.config(), classpaths),
                        classpaths,
                        classpathPackagesByMember.get(member.path())));
            } catch (JavacException exception) {
                throw new JavacException(
                        exception.getMessage()
                                + "\nWorkspace member `"
                                + member.path()
                                + "` failed to compile. If the missing type comes from a dependency of another workspace member, declare it directly in this member or move it to [api.dependencies] in the member that exposes it.",
                        exception);
            }
        }
        return new WorkspaceBuildResult(plan.resolveResult(), results);
    }

    private static Map<String, WorkspaceMember> membersByPath(Workspace workspace) {
        Map<String, WorkspaceMember> members = new LinkedHashMap<>();
        for (WorkspaceMember member : workspace.members()) {
            members.put(member.path(), member);
        }
        return members;
    }
}
