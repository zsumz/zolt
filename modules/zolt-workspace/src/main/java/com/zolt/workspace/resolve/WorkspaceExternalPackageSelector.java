package com.zolt.workspace.resolve;

import com.zolt.dependency.ConflictSelectionReason;
import com.zolt.dependency.DependencyScope;
import com.zolt.dependency.PackageId;
import com.zolt.dependency.VersionComparator;
import com.zolt.lockfile.LockConflict;
import com.zolt.lockfile.LockPackage;
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
            WorkspaceExternalSelection.VersionSelection selection = selectVersion(entry.getValue());
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
        return new WorkspaceExternalSelection(packages, conflicts);
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
                selectedTemplate.policies());
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
