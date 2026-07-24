package sh.zolt.workspace.resolve;

import sh.zolt.dependency.ConflictSelectionReason;
import sh.zolt.dependency.DependencyScope;
import sh.zolt.dependency.PackageId;
import sh.zolt.dependency.VersionComparator;
import sh.zolt.lockfile.LockArtifactVariant;
import sh.zolt.lockfile.LockConflict;
import sh.zolt.lockfile.LockPackage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

final class WorkspaceExternalPackageSelector {
    private static final VersionComparator VERSION_COMPARATOR = new VersionComparator();

    WorkspaceExternalSelection select(List<LockPackage> candidates) {
        List<LockPackage> regularCandidates = candidates.stream()
                .filter(candidate -> candidate.scope() != DependencyScope.TOOL_EXEC)
                .toList();
        List<LockPackage> execCandidates = candidates.stream()
                .filter(candidate -> candidate.scope() == DependencyScope.TOOL_EXEC)
                .toList();

        // Two variants of one GAV (a plain jar and a linux-x86_64 classified jar, or a jar and a .zip)
        // are distinct artifacts, so each gets its OWN lane keyed by (PackageId, variant). Version
        // mediation runs WITHIN a lane, so the same-GA different-variant entries coexist at their
        // mediated versions instead of one template collapsing the other's bytes.
        Map<PackageVariantKey, List<LockPackage>> candidatesByVariant = new LinkedHashMap<>();
        // A GA-level view still drives bare "g:a:v" dependency-edge rewriting and version-conflict
        // reporting: neither can name a classifier, so both stay keyed by PackageId exactly as before.
        Map<PackageId, List<LockPackage>> candidatesByPackage = new LinkedHashMap<>();
        regularCandidates.stream()
                .sorted(Comparator.comparing(lockPackage -> lockPackage.packageId()
                        + ":"
                        + lockPackage.version()
                        + ":"
                        + lockPackage.scope().lockfileName()
                        + ":"
                        + LockArtifactVariant.of(lockPackage).key()))
                .forEach(lockPackage -> {
                    candidatesByVariant
                            .computeIfAbsent(
                                    new PackageVariantKey(lockPackage.packageId(), LockArtifactVariant.of(lockPackage)),
                                    ignored -> new ArrayList<>())
                            .add(lockPackage);
                    candidatesByPackage
                            .computeIfAbsent(lockPackage.packageId(), ignored -> new ArrayList<>())
                            .add(lockPackage);
                });

        Map<PackageId, String> selectedVersions = new LinkedHashMap<>();
        Map<PackageId, ConflictSelectionReason> selectedReasons = new LinkedHashMap<>();
        for (Map.Entry<PackageId, List<LockPackage>> entry : candidatesByPackage.entrySet()) {
            WorkspaceExternalSelection.VersionSelection selection = selectVersion(entry.getValue());
            selectedVersions.put(entry.getKey(), selection.version());
            selectedReasons.put(entry.getKey(), selection.reason());
        }

        List<LockPackage> packages = new ArrayList<>();
        for (Map.Entry<PackageVariantKey, List<LockPackage>> entry : candidatesByVariant.entrySet()) {
            List<LockPackage> variantCandidates = entry.getValue();
            String variantSelectedVersion = selectVersion(variantCandidates).version();
            List<DependencyScope> scopes = variantCandidates.stream()
                    .map(LockPackage::scope)
                    .distinct()
                    .sorted(Comparator.comparing(DependencyScope::lockfileName))
                    .toList();
            for (DependencyScope scope : scopes) {
                packages.add(selectedPackage(variantCandidates, variantSelectedVersion, scope, selectedVersions));
            }
        }

        List<LockConflict> conflicts = new ArrayList<>();
        for (Map.Entry<PackageId, List<LockPackage>> entry : candidatesByPackage.entrySet()) {
            PackageId packageId = entry.getKey();
            List<String> requestedVersions = entry.getValue().stream()
                    .map(LockPackage::version)
                    .distinct()
                    .sorted(VERSION_COMPARATOR.thenComparing(Comparator.naturalOrder()))
                    .toList();
            if (requestedVersions.size() > 1) {
                conflicts.add(new LockConflict(
                        packageId,
                        selectedVersions.get(packageId),
                        requestedVersions,
                        selectedReasons.get(packageId)));
            }
        }
        packages.addAll(selectExecPackages(execCandidates));
        return new WorkspaceExternalSelection(packages, conflicts);
    }

    /** Identity of one aggregation lane: a {@link PackageId} plus its artifact variant. */
    private record PackageVariantKey(PackageId packageId, LockArtifactVariant variant) {
    }

    private static WorkspaceExternalSelection.VersionSelection selectVersion(List<LockPackage> candidates) {
        List<LockPackage> directCandidates = candidates.stream()
                .filter(LockPackage::direct)
                .toList();
        if (!directCandidates.isEmpty()) {
            return new WorkspaceExternalSelection.VersionSelection(
                    newestVersion(directCandidates),
                    ConflictSelectionReason.DIRECT_DEPENDENCY);
        }
        return new WorkspaceExternalSelection.VersionSelection(
                newestVersion(candidates),
                ConflictSelectionReason.NEWEST_VERSION);
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
                selectedTemplate.artifact(),
                selectedTemplate.artifactType(),
                selectedTemplate.artifactSha256(),
                selectedTemplate.workspace(),
                selectedTemplate.workspaceOutput(),
                rewriteDependencies(selectedTemplate.dependencies(), selectedVersions),
                List.copyOf(members),
                List.copyOf(exportedBy),
                selectedTemplate.policies(),
                List.of());
    }

    /**
     * Aggregates {@code tool-exec} candidates without version mediation. Each named exec tool keeps its
     * own locked version of a shared library, so entries are keyed by {@code (groupId:artifactId,
     * version, variant)} rather than {@code (GA, scope)}: two tools needing conflicting versions of the
     * same GA both survive into the aggregated root lock, as do two distinct artifact variants of one
     * GAV. A jar shared by several tools at the same version and variant collapses into one entry whose
     * {@code toolGroups} union (sorted), mirroring {@code ExecToolLockPlanner} in zolt-resolve.
     * Dependencies stay as locked by each tool's isolated closure and are never rewritten to a mediated
     * main version.
     */
    private static List<LockPackage> selectExecPackages(List<LockPackage> execCandidates) {
        Map<String, List<LockPackage>> execByCoordinateVersion = new LinkedHashMap<>();
        execCandidates.stream()
                .sorted(Comparator.comparing(WorkspaceExternalPackageSelector::execCoordinateVersionKey))
                .forEach(lockPackage -> execByCoordinateVersion
                        .computeIfAbsent(execCoordinateVersionKey(lockPackage), ignored -> new ArrayList<>())
                        .add(lockPackage));

        List<LockPackage> execPackages = new ArrayList<>();
        for (List<LockPackage> coordinateVersion : execByCoordinateVersion.values()) {
            execPackages.add(mergedExecPackage(coordinateVersion));
        }
        return execPackages;
    }

    private static String execCoordinateVersionKey(LockPackage lockPackage) {
        return lockPackage.packageId()
                + ":"
                + lockPackage.version()
                + ":"
                + LockArtifactVariant.of(lockPackage).key();
    }

    private static LockPackage mergedExecPackage(List<LockPackage> candidates) {
        LockPackage template = candidates.getFirst();
        boolean direct = candidates.stream().anyMatch(LockPackage::direct);
        Set<String> toolGroups = new LinkedHashSet<>();
        Set<String> members = new LinkedHashSet<>();
        Set<String> exportedBy = new LinkedHashSet<>();
        Set<String> dependencies = new LinkedHashSet<>();
        Set<String> policies = new LinkedHashSet<>();
        for (LockPackage candidate : candidates) {
            toolGroups.addAll(candidate.toolGroups());
            members.addAll(candidate.members());
            exportedBy.addAll(candidate.exportedBy());
            dependencies.addAll(candidate.dependencies());
            policies.addAll(candidate.policies());
        }
        return new LockPackage(
                template.packageId(),
                template.version(),
                template.source(),
                template.scope(),
                direct,
                template.jar(),
                template.pom(),
                template.jarSha256(),
                template.pomSha256(),
                template.artifact(),
                template.artifactType(),
                template.artifactSha256(),
                template.workspace(),
                template.workspaceOutput(),
                dependencies.stream().sorted().toList(),
                List.copyOf(members),
                List.copyOf(exportedBy),
                List.copyOf(policies),
                toolGroups.stream().sorted().toList());
    }

    private static List<String> rewriteDependencies(
            List<String> dependencies,
            Map<PackageId, String> selectedVersions) {
        return dependencies.stream()
                .map(dependency -> rewriteDependency(dependency, selectedVersions))
                .sorted()
                .toList();
    }

    private static String rewriteDependency(String dependency, Map<PackageId, String> selectedVersions) {
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
}
