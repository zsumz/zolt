package com.zolt.policy;

import com.zolt.lockfile.LockPackage;
import com.zolt.lockfile.LockPolicyEffect;
import com.zolt.lockfile.ZoltLockfile;
import com.zolt.project.DependencyConstraint;
import com.zolt.project.DependencyMetadata;
import com.zolt.project.DependencyPolicyExclusion;
import com.zolt.project.ProjectConfig;
import com.zolt.resolve.PackageId;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class DependencyPolicyReportService {
    public DependencyPolicyReport report(
            Path projectDirectory,
            ProjectConfig config,
            ZoltLockfile lockfile) {
        Path projectRoot = projectDirectory.toAbsolutePath().normalize();
        return new DependencyPolicyReport(
                projectRoot,
                platforms(config, lockfile),
                constraints(config, lockfile),
                exclusions(config, lockfile),
                directVersions(config, lockfile));
    }

    private static List<DependencyPolicyReport.PlatformPolicyDiagnostic> platforms(
            ProjectConfig config,
            ZoltLockfile lockfile) {
        Map<String, List<DependencyPolicyReport.ManagedPackageDiagnostic>> packagesByPlatform = new LinkedHashMap<>();
        config.platforms().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> packagesByPlatform.put(entry.getKey() + ":" + entry.getValue(), new ArrayList<>()));

        lockfile.packages().stream()
                .sorted(Comparator.comparing(DependencyPolicyReportService::packageSortKey))
                .forEach(lockPackage -> {
                    for (String policy : lockPackage.policies()) {
                        managedPlatform(policy).ifPresent(platform -> packagesByPlatform
                                .computeIfAbsent(platform, ignored -> new ArrayList<>())
                                .add(new DependencyPolicyReport.ManagedPackageDiagnostic(
                                        lockPackage.packageId().toString(),
                                        lockPackage.version(),
                                        lockPackage.scope().lockfileName(),
                                        policy)));
                    }
                });

        return packagesByPlatform.entrySet().stream()
                .map(entry -> new DependencyPolicyReport.PlatformPolicyDiagnostic(
                        entry.getKey(),
                        entry.getValue().stream()
                                .sorted(Comparator.comparing(DependencyPolicyReportService::managedPackageSortKey))
                                .toList()))
                .toList();
    }

    private static Optional<String> managedPlatform(String policy) {
        String prefix = "managed-version: ";
        int marker = policy.indexOf(" from ");
        if (!policy.startsWith(prefix) || marker < 0) {
            return Optional.empty();
        }
        String platform = policy.substring(marker + " from ".length()).trim();
        return platform.isBlank() ? Optional.empty() : Optional.of(platform);
    }

    private static List<DependencyPolicyReport.ConstraintPolicyDiagnostic> constraints(
            ProjectConfig config,
            ZoltLockfile lockfile) {
        return config.dependencyPolicy().constraints().values().stream()
                .sorted(Comparator.comparing(DependencyConstraint::coordinate))
                .map(constraint -> constraint(constraint, config, lockfile))
                .toList();
    }

    private static DependencyPolicyReport.ConstraintPolicyDiagnostic constraint(
            DependencyConstraint constraint,
            ProjectConfig config,
            ZoltLockfile lockfile) {
        PackageId packageId = packageId(constraint.coordinate());
        List<LockPackage> selectedPackages = selectedPackages(lockfile, packageId);
        Optional<String> selectedVersion = selectedPackages.stream()
                .map(LockPackage::version)
                .distinct()
                .sorted()
                .findFirst();
        List<LockPolicyEffect> effects = lockfile.policyEffects().stream()
                .filter(effect -> "strict-version".equals(effect.kind()))
                .filter(effect -> effect.packageId().equals(packageId))
                .sorted(Comparator.comparing(DependencyPolicyReportService::policyEffectSortKey))
                .toList();
        List<String> policies = effects.stream()
                .map(LockPolicyEffect::policy)
                .distinct()
                .sorted()
                .toList();
        Optional<String> source = effects.stream()
                .flatMap(effect -> effect.source().stream())
                .sorted()
                .findFirst();
        String status = constraintStatus(constraint, config, selectedPackages, effects);
        return new DependencyPolicyReport.ConstraintPolicyDiagnostic(
                constraint.coordinate(),
                constraint.kind().configValue(),
                constraint.version(),
                constraint.versionRef(),
                selectedVersion,
                status,
                source,
                constraint.reason(),
                policies);
    }

    private static String constraintStatus(
            DependencyConstraint constraint,
            ProjectConfig config,
            List<LockPackage> selectedPackages,
            List<LockPolicyEffect> effects) {
        if (selectedPackages.isEmpty()) {
            return "unmatched";
        }
        boolean selected = selectedPackages.stream()
                .anyMatch(lockPackage -> lockPackage.version().equals(constraint.version()));
        boolean directOverride = selectedPackages.stream()
                .anyMatch(lockPackage -> lockPackage.direct()
                        && hasExplicitDirectVersion(config, constraint.coordinate())
                        && !lockPackage.version().equals(constraint.version()));
        if (directOverride) {
            return "direct-override";
        }
        if (!selected) {
            return "conflict";
        }
        if (!effects.isEmpty()) {
            return "pinned";
        }
        return "selected";
    }

    private static List<DependencyPolicyReport.ExclusionPolicyDiagnostic> exclusions(
            ProjectConfig config,
            ZoltLockfile lockfile) {
        return config.dependencyPolicy().exclusions().stream()
                .sorted(Comparator.comparing(DependencyPolicyExclusion::coordinate))
                .map(exclusion -> exclusion(exclusion, lockfile))
                .toList();
    }

    private static DependencyPolicyReport.ExclusionPolicyDiagnostic exclusion(
            DependencyPolicyExclusion exclusion,
            ZoltLockfile lockfile) {
        PackageId packageId = new PackageId(exclusion.group(), exclusion.artifact());
        boolean directConflict = lockfile.packages().stream()
                .anyMatch(lockPackage -> lockPackage.packageId().equals(packageId) && lockPackage.direct());
        List<LockPolicyEffect> effects = lockfile.policyEffects().stream()
                .filter(effect -> "global-exclusion".equals(effect.kind()))
                .filter(effect -> effect.packageId().equals(packageId))
                .sorted(Comparator.comparing(DependencyPolicyReportService::policyEffectSortKey))
                .toList();
        String status = directConflict ? "direct-conflict" : effects.isEmpty() ? "unmatched" : "matched";
        return new DependencyPolicyReport.ExclusionPolicyDiagnostic(
                exclusion.coordinate(),
                status,
                exclusion.reason(),
                effects.stream()
                        .flatMap(effect -> effect.source().stream())
                        .distinct()
                        .sorted()
                        .toList(),
                effects.stream()
                        .map(LockPolicyEffect::policy)
                        .distinct()
                        .sorted()
                        .toList());
    }

    private static List<DependencyPolicyReport.DirectVersionDiagnostic> directVersions(
            ProjectConfig config,
            ZoltLockfile lockfile) {
        Map<String, DirectDependency> directDependencies = explicitDirectVersions(config);
        return directDependencies.values().stream()
                .sorted(Comparator.comparing(direct -> direct.section() + ":" + direct.coordinate()))
                .map(direct -> new DependencyPolicyReport.DirectVersionDiagnostic(
                        direct.section(),
                        direct.coordinate(),
                        direct.version(),
                        direct.versionRef(),
                        directVersionStatus(direct, lockfile)))
                .toList();
    }

    private static String directVersionStatus(DirectDependency direct, ZoltLockfile lockfile) {
        PackageId packageId = packageId(direct.coordinate());
        return selectedPackages(lockfile, packageId).stream()
                .filter(LockPackage::direct)
                .anyMatch(lockPackage -> lockPackage.version().equals(direct.version()))
                ? "selected"
                : "not-selected";
    }

    private static Map<String, DirectDependency> explicitDirectVersions(ProjectConfig config) {
        Map<String, DirectDependency> directDependencies = new LinkedHashMap<>();
        addDirectVersions(
                directDependencies, "api.dependencies", config.apiDependencies(), config.dependencyMetadata());
        addDirectVersions(
                directDependencies, "dependencies", config.dependencies(), config.dependencyMetadata());
        addDirectVersions(
                directDependencies, "runtime.dependencies", config.runtimeDependencies(), config.dependencyMetadata());
        addDirectVersions(
                directDependencies, "provided.dependencies", config.providedDependencies(), config.dependencyMetadata());
        addDirectVersions(
                directDependencies, "dev.dependencies", config.devDependencies(), config.dependencyMetadata());
        addDirectVersions(
                directDependencies, "test.dependencies", config.testDependencies(), config.dependencyMetadata());
        addDirectVersions(
                directDependencies, "annotationProcessors", config.annotationProcessors(), config.dependencyMetadata());
        addDirectVersions(
                directDependencies,
                "test.annotationProcessors",
                config.testAnnotationProcessors(),
                config.dependencyMetadata());
        return directDependencies;
    }

    private static boolean hasExplicitDirectVersion(ProjectConfig config, String coordinate) {
        return explicitDirectVersions(config).values().stream()
                .anyMatch(direct -> direct.coordinate().equals(coordinate));
    }

    private static void addDirectVersions(
            Map<String, DirectDependency> directDependencies,
            String section,
            Map<String, String> dependencies,
            Map<String, DependencyMetadata> dependencyMetadata) {
        dependencies.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    DependencyMetadata metadata =
                            dependencyMetadata.get(DependencyMetadata.key(section, entry.getKey()));
                    directDependencies.put(
                            section + ":" + entry.getKey(),
                            new DirectDependency(
                                    section,
                                    entry.getKey(),
                                    entry.getValue(),
                                    metadata == null
                                            ? Optional.empty()
                                            : Optional.ofNullable(metadata.versionRef())));
                });
    }

    private static List<LockPackage> selectedPackages(ZoltLockfile lockfile, PackageId packageId) {
        return lockfile.packages().stream()
                .filter(lockPackage -> lockPackage.packageId().equals(packageId))
                .sorted(Comparator.comparing(DependencyPolicyReportService::packageSortKey))
                .toList();
    }

    private static PackageId packageId(String coordinate) {
        String[] parts = coordinate.split(":", -1);
        if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            throw new DependencyPolicyReportException(
                    "Dependency policy coordinate `"
                            + coordinate
                            + "` must use group:artifact.");
        }
        return new PackageId(parts[0], parts[1]);
    }

    private static String managedPackageSortKey(DependencyPolicyReport.ManagedPackageDiagnostic diagnostic) {
        return diagnostic.coordinate() + ":" + diagnostic.version() + ":" + diagnostic.scope();
    }

    private static String packageSortKey(LockPackage lockPackage) {
        return lockPackage.packageId()
                + ":"
                + lockPackage.version()
                + ":"
                + lockPackage.scope().lockfileName();
    }

    private static String policyEffectSortKey(LockPolicyEffect effect) {
        return effect.kind()
                + ":"
                + effect.packageId()
                + ":"
                + effect.requestedVersion().orElse("")
                + ":"
                + effect.source().orElse("")
                + ":"
                + effect.policy();
    }

    private record DirectDependency(
            String section,
            String coordinate,
            String version,
            Optional<String> versionRef) {}
}
