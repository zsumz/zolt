package sh.zolt.quality;

import static sh.zolt.quality.QualityCheckService.DEPENDENCY_METADATA;

import sh.zolt.dependency.PackageId;
import sh.zolt.lockfile.LockPackage;
import sh.zolt.lockfile.toml.LockfileReadException;
import sh.zolt.lockfile.ZoltLockfile;
import sh.zolt.lockfile.toml.ZoltLockfileReader;
import sh.zolt.policy.DependencyPolicyReportService;
import sh.zolt.project.DependencyMetadata;
import sh.zolt.project.ProjectConfig;
import sh.zolt.workspace.service.Workspace;
import sh.zolt.workspace.service.WorkspaceMember;
import sh.zolt.workspace.service.WorkspaceSelection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

final class DependencyQualityCheck {
    private final ZoltLockfileReader lockfileReader;
    private final DependencyPolicyQualityCheck dependencyPolicyQualityCheck;

    DependencyQualityCheck(
            ZoltLockfileReader lockfileReader,
            DependencyPolicyReportService dependencyPolicyReportService) {
        this.lockfileReader = lockfileReader;
        this.dependencyPolicyQualityCheck = new DependencyPolicyQualityCheck(
                lockfileReader,
                dependencyPolicyReportService);
    }

    List<QualityCheckResult> checkProjectMetadata(
            Optional<String> member,
            Path root,
            ProjectConfig config,
            boolean workspaceLockfile) {
        Path lockfilePath = root.resolve("zolt.lock");
        if (!Files.isRegularFile(lockfilePath)) {
            return List.of(QualityCheckResult.failed(
                    DEPENDENCY_METADATA,
                    member,
                    "zolt.lock",
                    (workspaceLockfile ? "Workspace zolt.lock" : "zolt.lock") + " is missing.",
                    workspaceLockfile ? "Run `zolt resolve --workspace`." : "Run `zolt resolve`."));
        }

        ZoltLockfile lockfile;
        try {
            lockfile = lockfileReader.read(lockfilePath);
        } catch (LockfileReadException exception) {
            return List.of(QualityCheckResult.failed(
                    DEPENDENCY_METADATA,
                    member,
                    "zolt.lock",
                    exception.getMessage(),
                    workspaceLockfile ? "Run `zolt resolve --workspace`." : "Run `zolt resolve`."));
        }

        return checkDependencyMetadataDeclarations(member, config, lockfile, workspaceLockfile);
    }

    List<QualityCheckResult> checkWorkspaceMetadata(
            Workspace workspace,
            WorkspaceSelection selection,
            Map<String, WorkspaceMember> members) {
        Path lockfilePath = workspace.root().resolve("zolt.lock");
        if (!Files.isRegularFile(lockfilePath)) {
            return List.of(QualityCheckResult.failed(
                    DEPENDENCY_METADATA,
                    Optional.empty(),
                    "zolt.lock",
                    "Workspace zolt.lock is missing.",
                    "Run `zolt resolve --workspace`."));
        }

        ZoltLockfile lockfile;
        try {
            lockfile = lockfileReader.read(lockfilePath);
        } catch (LockfileReadException exception) {
            return List.of(QualityCheckResult.failed(
                    DEPENDENCY_METADATA,
                    Optional.empty(),
                    "zolt.lock",
                    exception.getMessage(),
                    "Run `zolt resolve --workspace`."));
        }

        List<QualityCheckResult> results = new ArrayList<>();
        for (String memberPath : selection.includedMembers()) {
            WorkspaceMember member = members.get(memberPath);
            results.addAll(checkDependencyMetadataDeclarations(
                    Optional.of(member.path()),
                    member.config(),
                    lockfile,
                    true));
            results.addAll(checkWorkspaceApiEdges(workspace, member, lockfile));
        }
        if (results.isEmpty()) {
            results.add(QualityCheckResult.passed(
                    DEPENDENCY_METADATA,
                    Optional.empty(),
                    workspace.config().name(),
                    "No dependency metadata declarations require validation."));
        }
        return List.copyOf(results);
    }

    List<QualityCheckResult> checkPolicy(
            Optional<String> member,
            Path root,
            ProjectConfig config,
            Path lockfilePath,
            boolean workspaceLockfile) {
        return dependencyPolicyQualityCheck.check(member, root, config, lockfilePath, workspaceLockfile);
    }

    private List<QualityCheckResult> checkDependencyMetadataDeclarations(
            Optional<String> member,
            ProjectConfig config,
            ZoltLockfile lockfile,
            boolean workspaceLockfile) {
        if (config.dependencyMetadata().isEmpty()) {
            return List.of(QualityCheckResult.passed(
                    DEPENDENCY_METADATA,
                    member,
                    config.project().name(),
                    "No dependency metadata declarations require validation."));
        }

        List<QualityCheckResult> results = new ArrayList<>();
        for (DependencyMetadata metadata : new TreeMap<>(config.dependencyMetadata()).values()) {
            if (metadata.workspace() != null) {
                if (metadata.optional()) {
                    results.add(QualityCheckResult.failed(
                            DEPENDENCY_METADATA,
                            member,
                            metadata.coordinate(),
                            "Workspace dependency `" + metadata.coordinate() + "` declares optional metadata, which is not supported.",
                            "Remove optional = true or use an external dependency coordinate."));
                }
                continue;
            }

            if (metadata.publishOnly()) {
                results.add(checkPublishOnlyMetadata(member, metadata, lockfile));
                continue;
            }

            results.add(checkClasspathMetadata(member, metadata, lockfile, workspaceLockfile));
        }
        return List.copyOf(results);
    }

    private QualityCheckResult checkPublishOnlyMetadata(
            Optional<String> member,
            DependencyMetadata metadata,
            ZoltLockfile lockfile) {
        Optional<LockPackage> lockPackage = findLockPackage(lockfile, packageId(metadata.coordinate()), member);
        if (lockPackage.isPresent()) {
            return QualityCheckResult.failed(
                    DEPENDENCY_METADATA,
                    member,
                    metadata.coordinate(),
                    "Publish-only dependency `" + metadata.coordinate() + "` is present in zolt.lock.",
                    "Run `zolt resolve`; if it remains, remove publishOnly = true or move the dependency to a normal classpath section.");
        }
        return QualityCheckResult.passed(
                DEPENDENCY_METADATA,
                member,
                metadata.coordinate(),
                "Publish-only dependency `" + metadata.coordinate() + "` is kept out of zolt.lock classpaths.");
    }

    private QualityCheckResult checkClasspathMetadata(
            Optional<String> member,
            DependencyMetadata metadata,
            ZoltLockfile lockfile,
            boolean workspaceLockfile) {
        Optional<LockPackage> maybeLockPackage = findLockPackage(lockfile, packageId(metadata.coordinate()), member);
        if (maybeLockPackage.isEmpty()) {
            return QualityCheckResult.failed(
                    DEPENDENCY_METADATA,
                    member,
                    metadata.coordinate(),
                    "Dependency metadata for `" + metadata.coordinate() + "` is not represented in zolt.lock.",
                    workspaceLockfile ? "Run `zolt resolve --workspace`." : "Run `zolt resolve`.");
        }

        LockPackage lockPackage = maybeLockPackage.orElseThrow();
        if (metadata.optional() && !lockPackage.direct()) {
            return QualityCheckResult.failed(
                    DEPENDENCY_METADATA,
                    member,
                    metadata.coordinate(),
                    "Optional direct dependency `" + metadata.coordinate() + "` is not marked direct in zolt.lock.",
                    workspaceLockfile ? "Run `zolt resolve --workspace`." : "Run `zolt resolve`.");
        }

        for (sh.zolt.project.DependencyExclusionSpec exclusion : metadata.exclusions()) {
            if (lockPackage.dependencies().contains(exclusion.coordinate())) {
                return QualityCheckResult.failed(
                        DEPENDENCY_METADATA,
                        member,
                        metadata.coordinate(),
                        "Excluded dependency `" + exclusion.coordinate() + "` is still present on direct dependency `" + metadata.coordinate() + "` in zolt.lock.",
                        "Check [" + metadata.section() + "]." + metadata.coordinate() + ".exclusions and run "
                                + (workspaceLockfile ? "`zolt resolve --workspace`." : "`zolt resolve`."));
            }
        }

        return QualityCheckResult.passed(
                DEPENDENCY_METADATA,
                member,
                metadata.coordinate(),
                "Dependency metadata for `" + metadata.coordinate() + "` is represented in zolt.lock.");
    }

    private static List<QualityCheckResult> checkWorkspaceApiEdges(
            Workspace workspace,
            WorkspaceMember member,
            ZoltLockfile lockfile) {
        List<QualityCheckResult> results = new ArrayList<>();
        for (Map.Entry<String, String> dependency : new TreeMap<>(member.config().workspaceApiDependencies()).entrySet()) {
            String coordinate = dependency.getKey();
            String target = normalizeMemberPath(dependency.getValue());
            Optional<sh.zolt.workspace.service.WorkspaceProjectEdge> edge = workspace.edges().stream()
                    .filter(candidate -> candidate.from().equals(member.path())
                            && candidate.to().equals(target)
                            && candidate.coordinate().equals(coordinate))
                    .findFirst();
            if (edge.isEmpty() || !edge.orElseThrow().exported()) {
                results.add(QualityCheckResult.failed(
                        DEPENDENCY_METADATA,
                        Optional.of(member.path()),
                        coordinate,
                        "Workspace API dependency `" + coordinate + "` is not represented as an exported workspace edge.",
                        "Keep public workspace dependencies in [api.dependencies] and run `zolt resolve --workspace`."));
                continue;
            }

            Optional<LockPackage> packageNode = lockfile.packages().stream()
                    .filter(lockPackage -> lockPackage.packageId().equals(packageId(coordinate))
                            && lockPackage.workspace().orElse("").equals(target))
                    .findFirst();
            if (packageNode.isEmpty() || !packageNode.orElseThrow().exportedBy().contains(member.path())) {
                results.add(QualityCheckResult.failed(
                        DEPENDENCY_METADATA,
                        Optional.of(member.path()),
                        coordinate,
                        "Workspace API dependency `" + coordinate + "` is missing exportedBy ownership in zolt.lock.",
                        "Run `zolt resolve --workspace`."));
                continue;
            }

            results.add(QualityCheckResult.passed(
                    DEPENDENCY_METADATA,
                    Optional.of(member.path()),
                    coordinate,
                    "Workspace API dependency `" + coordinate + "` is exported through zolt.lock."));
        }
        return List.copyOf(results);
    }

    private static Optional<LockPackage> findLockPackage(
            ZoltLockfile lockfile,
            PackageId packageId,
            Optional<String> member) {
        return lockfile.packages().stream()
                .filter(lockPackage -> lockPackage.packageId().equals(packageId))
                .filter(lockPackage -> member.isEmpty()
                        || lockPackage.members().isEmpty()
                        || lockPackage.members().contains(member.orElseThrow()))
                .findFirst();
    }

    private static PackageId packageId(String coordinate) {
        String[] parts = coordinate.split(":", -1);
        return new PackageId(parts[0], parts[1]);
    }

    private static String normalizeMemberPath(String path) {
        String normalized = Path.of(path).normalize().toString().replace('\\', '/');
        return normalized.isBlank() ? "." : normalized;
    }
}
