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
import com.zolt.project.RepositorySettings;
import com.zolt.dependency.DependencyScope;
import com.zolt.dependency.PackageId;
import com.zolt.dependency.ConflictSelectionReason;
import com.zolt.resolve.ResolveException;
import com.zolt.resolve.ResolveMetrics;
import com.zolt.resolve.ResolveOutput;
import com.zolt.resolve.ResolveResult;
import com.zolt.resolve.ResolveService;
import com.zolt.dependency.VersionComparator;
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
    private static final VersionComparator VERSION_COMPARATOR = new VersionComparator();

    private final WorkspaceDiscoveryService workspaceDiscoveryService;
    private final ResolveService resolveService;
    private final ZoltLockfileWriter lockfileWriter;

    public WorkspaceResolveService() {
        this(new WorkspaceDiscoveryService(), new ResolveService(), new ZoltLockfileWriter());
    }

    public WorkspaceResolveService(ResolveService resolveService) {
        this(new WorkspaceDiscoveryService(), resolveService, new ZoltLockfileWriter());
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
        ResolveMetrics metrics = ResolveMetrics.empty();
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

    private static ProjectConfig mergeWorkspacePolicy(Workspace workspace, WorkspaceMember member) {
        ProjectConfig config = member.config();
        Map<String, String> repositories = mergedPolicy(
                "repository",
                workspace,
                member,
                workspace.config().repositories(),
                config.repositories());
        return new ProjectConfig(
                config.project(),
                repositories,
                repositorySettings(repositories),
                Map.of(),
                config.versionAliases(),
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
                config.runtimeDependencies(),
                config.managedRuntimeDependencies(),
                config.providedDependencies(),
                config.managedProvidedDependencies(),
                config.devDependencies(),
                config.managedDevDependencies(),
                config.testDependencies(),
                config.managedTestDependencies(),
                config.workspaceTestDependencies(),
                config.annotationProcessors(),
                config.managedAnnotationProcessors(),
                config.testAnnotationProcessors(),
                config.managedTestAnnotationProcessors(),
                config.dependencyPolicy(),
                config.build(),
                config.nativeSettings(),
                config.compilerSettings(),
                config.packageSettings(),
                config.frameworkSettings(),
                config.dependencyMetadata());
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

    private static Map<String, RepositorySettings> repositorySettings(Map<String, String> repositories) {
        Map<String, RepositorySettings> settings = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : repositories.entrySet()) {
            settings.put(entry.getKey(), RepositorySettings.unauthenticated(entry.getKey(), entry.getValue()));
        }
        return settings;
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

        GlobalExternalSelection globalSelection = selectGlobalExternalPackages(externalCandidates);
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

    private static GlobalExternalSelection selectGlobalExternalPackages(List<LockPackage> candidates) {
        Map<PackageId, List<LockPackage>> candidatesByPackage = new LinkedHashMap<>();
        candidates.stream()
                .sorted(Comparator.comparing(lockPackage -> lockPackage.packageId()
                        + ":"
                        + lockPackage.version()
                        + ":"
                        + lockPackage.scope().lockfileName()))
                .forEach(lockPackage -> candidatesByPackage
                        .computeIfAbsent(lockPackage.packageId(), ignored -> new ArrayList<>())
                        .add(lockPackage));

        Map<PackageId, String> selectedVersions = new LinkedHashMap<>();
        Map<PackageId, ConflictSelectionReason> selectedReasons = new LinkedHashMap<>();
        for (Map.Entry<PackageId, List<LockPackage>> entry : candidatesByPackage.entrySet()) {
            VersionSelection selection = selectVersion(entry.getValue());
            selectedVersions.put(entry.getKey(), selection.version());
            selectedReasons.put(entry.getKey(), selection.reason());
        }

        List<LockPackage> packages = new ArrayList<>();
        List<LockConflict> conflicts = new ArrayList<>();
        for (Map.Entry<PackageId, List<LockPackage>> entry : candidatesByPackage.entrySet()) {
            PackageId packageId = entry.getKey();
            List<LockPackage> packageCandidates = entry.getValue();
            String selectedVersion = selectedVersions.get(packageId);
            List<DependencyScope> scopes = packageCandidates.stream()
                    .map(LockPackage::scope)
                    .distinct()
                    .sorted(Comparator.comparing(DependencyScope::lockfileName))
                    .toList();
            for (DependencyScope scope : scopes) {
                packages.add(selectedPackage(packageCandidates, selectedVersion, scope, selectedVersions));
            }

            List<String> requestedVersions = packageCandidates.stream()
                    .map(LockPackage::version)
                    .distinct()
                    .sorted(VERSION_COMPARATOR.thenComparing(Comparator.naturalOrder()))
                    .toList();
            if (requestedVersions.size() > 1) {
                conflicts.add(new LockConflict(
                        packageId,
                        selectedVersion,
                        requestedVersions,
                        selectedReasons.get(packageId)));
            }
        }
        return new GlobalExternalSelection(packages, conflicts);
    }

    private static VersionSelection selectVersion(List<LockPackage> candidates) {
        List<LockPackage> directCandidates = candidates.stream()
                .filter(LockPackage::direct)
                .toList();
        if (!directCandidates.isEmpty()) {
            return new VersionSelection(newestVersion(directCandidates), ConflictSelectionReason.DIRECT_DEPENDENCY);
        }
        return new VersionSelection(newestVersion(candidates), ConflictSelectionReason.NEWEST_VERSION);
    }

    private static String newestVersion(List<LockPackage> candidates) {
        return candidates.stream()
                .map(LockPackage::version)
                .max(VERSION_COMPARATOR)
                .orElseThrow();
    }

    private static LockPackage selectedPackage(
            List<LockPackage> packageCandidates,
            String selectedVersion,
            DependencyScope scope,
            Map<PackageId, String> selectedVersions) {
        LockPackage selectedTemplate = packageCandidates.stream()
                .filter(lockPackage -> lockPackage.version().equals(selectedVersion))
                .findFirst()
                .orElseThrow();
        List<LockPackage> scopeCandidates = packageCandidates.stream()
                .filter(lockPackage -> lockPackage.scope() == scope)
                .toList();
        boolean direct = scopeCandidates.stream().anyMatch(LockPackage::direct);
        Set<String> members = new LinkedHashSet<>();
        Set<String> exportedBy = new LinkedHashSet<>();
        for (LockPackage candidate : scopeCandidates) {
            members.addAll(candidate.members());
            exportedBy.addAll(candidate.exportedBy());
        }
        return new LockPackage(
                selectedTemplate.packageId(),
                selectedVersion,
                selectedTemplate.source(),
                scope,
                direct,
                selectedTemplate.jar(),
                selectedTemplate.pom(),
                selectedTemplate.jarSha256(),
                selectedTemplate.pomSha256(),
                selectedTemplate.workspace(),
                selectedTemplate.workspaceOutput(),
                rewriteDependencies(selectedTemplate.dependencies(), selectedVersions),
                List.copyOf(members),
                List.copyOf(exportedBy));
    }

    private static List<String> rewriteDependencies(
            List<String> dependencies,
            Map<PackageId, String> selectedVersions) {
        return dependencies.stream()
                .map(dependency -> rewriteDependency(dependency, selectedVersions))
                .sorted()
                .toList();
    }

    private static String rewriteDependency(
            String dependency,
            Map<PackageId, String> selectedVersions) {
        String[] parts = dependency.split(":", -1);
        if (parts.length != 3 || parts[0].isBlank() || parts[1].isBlank()) {
            return dependency;
        }
        PackageId packageId = new PackageId(parts[0], parts[1]);
        String selectedVersion = selectedVersions.get(packageId);
        if (selectedVersion == null) {
            return dependency;
        }
        return packageId + ":" + selectedVersion;
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
                    Optional.of(workspaceOutput(workspace.root(), target)),
                    List.of(),
                    List.of(edge.from()),
                    edge.exported() ? List.of(edge.from()) : List.of()));
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

    private record GlobalExternalSelection(
            List<LockPackage> packages,
            List<LockConflict> conflicts) {
        private GlobalExternalSelection {
            packages = List.copyOf(packages);
            conflicts = List.copyOf(conflicts);
        }
    }

    private record VersionSelection(
            String version,
            ConflictSelectionReason reason) {
    }
}
