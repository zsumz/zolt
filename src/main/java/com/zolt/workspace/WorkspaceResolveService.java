package com.zolt.workspace;

import com.zolt.lockfile.LockConflict;
import com.zolt.lockfile.LockfileFreshnessSummary;
import com.zolt.lockfile.LockfileReadException;
import com.zolt.lockfile.LockPackage;
import com.zolt.lockfile.LockPolicyEffect;
import com.zolt.lockfile.ZoltLockfile;
import com.zolt.lockfile.ZoltLockfileReader;
import com.zolt.lockfile.ZoltLockfileWriter;
import com.zolt.project.ProjectConfig;
import com.zolt.project.ProjectPathException;
import com.zolt.project.ProjectPaths;
import com.zolt.dependency.DependencyScope;
import com.zolt.dependency.PackageId;
import com.zolt.resolve.ResolveException;
import com.zolt.resolve.ResolveMetrics;
import com.zolt.resolve.ResolveOutput;
import com.zolt.resolve.ResolveResult;
import com.zolt.resolve.ResolveService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
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
    private final WorkspacePolicyMerger policyMerger;

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
        ResolveMetrics metrics = ResolveMetrics.empty();
        for (String memberPath : workspace.buildOrder()) {
            WorkspaceMember member = membersByPath.get(memberPath);
            ResolveOutput output = resolveService.resolveLockfile(
                    policyMerger.merge(workspace, member),
                    cacheRoot,
                    offline);
            memberOutputs.add(new MemberResolveOutput(
                    member.path(),
                    output.lockfile(),
                    exportedExternalPackageIds(member.config())));
            downloadCount += output.downloadCount();
            metrics = metrics.plus(output.metrics());
        }

        ZoltLockfile lockfile = aggregate(workspace, memberOutputs);
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

    private static ZoltLockfile aggregate(Workspace workspace, List<MemberResolveOutput> memberOutputs) {
        Map<String, LockPackage> packages = new LinkedHashMap<>();
        Map<String, LockConflict> conflicts = new LinkedHashMap<>();
        Map<String, LockPolicyEffect> policyEffects = new LinkedHashMap<>();
        for (LockPackage lockPackage : workspacePackages(workspace)) {
            String key = packageKey(lockPackage);
            LockPackage existingPackage = packages.get(key);
            packages.put(key, existingPackage == null ? lockPackage : merge(existingPackage, lockPackage));
        }

        List<LockPackage> externalCandidates = new ArrayList<>();
        for (MemberResolveOutput memberOutput : memberOutputs) {
            for (LockPackage lockPackage : memberOutput.lockfile().packages()) {
                if (!lockPackage.workspace().isPresent()) {
                    externalCandidates.add(withMember(lockPackage, memberOutput.member(), memberOutput.exportedPackageIds()));
                }
            }
            for (LockConflict conflict : memberOutput.lockfile().conflicts()) {
                conflicts.putIfAbsent(conflictKey(conflict), conflict);
            }
            for (LockPolicyEffect policyEffect : memberOutput.lockfile().policyEffects()) {
                policyEffects.putIfAbsent(policyEffectKey(policyEffect), policyEffect);
            }
        }

        WorkspaceExternalSelection globalSelection = new WorkspaceExternalPackageSelector().select(externalCandidates);
        for (LockPackage lockPackage : globalSelection.packages()) {
            String key = packageKey(lockPackage);
            LockPackage existingPackage = packages.get(key);
            packages.put(key, existingPackage == null ? lockPackage : merge(existingPackage, lockPackage));
        }
        for (LockConflict conflict : globalSelection.conflicts()) {
            conflicts.put(conflictKey(conflict), conflict);
        }

        return new ZoltLockfile(
                ZoltLockfile.CURRENT_VERSION,
                workspaceAliasFingerprint(memberOutputs),
                workspaceProjectResolutionFingerprint(memberOutputs),
                workspaceProjectResolutionInputFingerprints(memberOutputs),
                List.copyOf(packages.values()),
                List.copyOf(conflicts.values()),
                List.copyOf(policyEffects.values()));
    }

    private static Optional<String> workspaceAliasFingerprint(List<MemberResolveOutput> memberOutputs) {
        List<String> inputs = memberOutputs.stream()
                .filter(output -> output.lockfile().aliasFingerprint().isPresent())
                .sorted(Comparator.comparing(MemberResolveOutput::member))
                .map(output -> output.member() + "\t" + output.lockfile().aliasFingerprint().orElseThrow())
                .toList();
        if (inputs.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of("sha256:" + sha256((String.join("\n", inputs) + "\n").getBytes(StandardCharsets.UTF_8)));
    }

    private static Optional<String> workspaceProjectResolutionFingerprint(List<MemberResolveOutput> memberOutputs) {
        List<String> inputs = memberOutputs.stream()
                .filter(output -> output.lockfile().projectResolutionFingerprint().isPresent())
                .sorted(Comparator.comparing(MemberResolveOutput::member))
                .map(output -> output.member() + "\t" + output.lockfile().projectResolutionFingerprint().orElseThrow())
                .toList();
        if (inputs.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of("sha256:" + sha256((String.join("\n", inputs) + "\n").getBytes(StandardCharsets.UTF_8)));
    }

    private static List<String> workspaceProjectResolutionInputFingerprints(List<MemberResolveOutput> memberOutputs) {
        Map<String, List<String>> inputs = new LinkedHashMap<>();
        memberOutputs.stream()
                .sorted(Comparator.comparing(MemberResolveOutput::member))
                .forEach(output -> output.lockfile().projectResolutionInputFingerprints().forEach(input -> {
                    int separator = input.indexOf('=');
                    if (separator <= 0 || separator == input.length() - 1) {
                        return;
                    }
                    inputs.computeIfAbsent(input.substring(0, separator), ignored -> new ArrayList<>())
                            .add(output.member() + "\t" + input.substring(separator + 1));
                }));
        return inputs.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + "=sha256:" + sha256((String.join("\n", entry.getValue()) + "\n")
                        .getBytes(StandardCharsets.UTF_8)))
                .toList();
    }

    private static String sha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes);
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte value : hash) {
                hex.append(String.format("%02x", value));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new ResolveException("Could not compute workspace alias fingerprint because SHA-256 is unavailable.", exception);
        }
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
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.of(edge.to()),
                    Optional.of(workspaceOutput(workspace.root(), target)),
                    List.of(),
                    List.of(edge.from()),
                    edge.exported() ? List.of(edge.from()) : List.of(),
                    List.of()));
        }
        return packages;
    }

    private static String workspaceOutput(Path workspaceRoot, WorkspaceMember member) {
        String configuredOutput = member.config().build().output();
        try {
            Path memberRoot = ProjectPaths.existingRoot(
                    ProjectPaths.root(workspaceRoot),
                    "[workspace].members",
                    member.path());
            ProjectPaths.output(memberRoot, "[build].output", configuredOutput);
            return configuredOutput;
        } catch (ProjectPathException exception) {
            throw new ResolveException(
                    "Workspace member `"
                            + member.path()
                            + "` has an invalid [build].output. "
                            + exception.getMessage(),
                    exception);
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
                firstPresent(left.artifact(), right.artifact()),
                firstPresent(left.artifactType(), right.artifactType()),
                firstPresent(left.artifactSha256(), right.artifactSha256()),
                firstPresent(left.workspace(), right.workspace()),
                firstPresent(left.workspaceOutput(), right.workspaceOutput()),
                List.copyOf(dependencies),
                List.copyOf(members),
                List.copyOf(exportedBy),
                merged(left.policies(), right.policies()));
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
                lockPackage.artifact(),
                lockPackage.artifactType(),
                lockPackage.artifactSha256(),
                lockPackage.workspace(),
                lockPackage.workspaceOutput(),
                lockPackage.dependencies(),
                List.copyOf(members),
                List.copyOf(exportedBy),
                lockPackage.policies());
    }

    private static List<String> merged(List<String> left, List<String> right) {
        Set<String> values = new LinkedHashSet<>(left);
        values.addAll(right);
        return List.copyOf(values);
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

    private static String policyEffectKey(LockPolicyEffect policyEffect) {
        return policyEffect.kind()
                + ":"
                + policyEffect.packageId()
                + ":"
                + policyEffect.requestedVersion().orElse("")
                + ":"
                + policyEffect.source().orElse("")
                + ":"
                + policyEffect.policy();
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
}
