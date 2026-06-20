package com.zolt.resolve;

import com.zolt.dependency.DependencyScope;
import com.zolt.dependency.PackageId;
import com.zolt.lockfile.LockPolicyEffect;
import com.zolt.project.DependencyConstraint;
import com.zolt.project.DependencyMetadata;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

final class LockfilePolicyPlanner {
    private LockfilePolicyPlanner() {
    }

    static List<String> policiesFor(
            PackageNode node,
            SelectedDependencyScope selectedScope,
            Map<String, DependencyConstraint> constraints,
            Map<PackageId, List<DependencyScope>> managedDirectScopes,
            Map<PackageId, ManagedVersion> managedVersions,
            Map<String, DependencyMetadata> dependencyMetadata,
            List<DependencyPolicyEffect> policyEffects) {
        List<String> policies = new ArrayList<>();
        if (selectedScope.direct()) {
            policies.addAll(versionRefPolicies(node, selectedScope, dependencyMetadata));
        }
        if (selectedScope.direct()
                && managedDirectScopes.getOrDefault(node.packageId(), List.of()).contains(selectedScope.scope())) {
            ManagedVersion managedVersion = managedVersions.get(node.packageId());
            if (managedVersion != null && managedVersion.version().equals(node.selectedVersion())) {
                policies.add("managed-version: "
                        + node.packageId()
                        + " -> "
                        + managedVersion.version()
                        + " from "
                        + managedVersion.platform());
            }
        }
        if (selectedScope.direct()) {
            return policies;
        }
        DependencyConstraint constraint = constraints.get(node.packageId().toString());
        if (constraint == null || !constraint.version().equals(node.selectedVersion())) {
            return policies;
        }
        List<String> strictPolicies = policyEffects.stream()
                .filter(effect -> "strict-version".equals(effect.kind()))
                .filter(effect -> effect.packageId().equals(node.packageId()))
                .map(DependencyPolicyEffect::policy)
                .distinct()
                .sorted()
                .toList();
        if (strictPolicies.isEmpty()) {
            String policy = "strict-version: " + node.packageId() + " -> " + constraint.version();
            policies.add(constraint.reason()
                    .map(reason -> policy + " (" + reason + ")")
                    .orElse(policy));
        } else {
            policies.addAll(strictPolicies);
        }
        return List.copyOf(policies);
    }

    static List<LockPolicyEffect> lockPolicyEffects(List<DependencyPolicyEffect> policyEffects) {
        return policyEffects.stream()
                .map(effect -> new LockPolicyEffect(
                        effect.kind(),
                        effect.packageId(),
                        effect.requestedVersion(),
                        effect.source(),
                        effect.policy()))
                .distinct()
                .sorted(Comparator.comparing(effect -> effect.kind()
                        + ":"
                        + effect.packageId()
                        + ":"
                        + effect.requestedVersion().orElse("")
                        + ":"
                        + effect.source().orElse("")
                        + ":"
                        + effect.policy()))
                .toList();
    }

    private static List<String> versionRefPolicies(
            PackageNode node,
            SelectedDependencyScope selectedScope,
            Map<String, DependencyMetadata> dependencyMetadata) {
        return dependencyMetadata.values().stream()
                .filter(metadata -> metadata.versionRef() != null)
                .filter(metadata -> metadata.coordinate().equals(node.packageId().toString()))
                .filter(metadata -> node.selectedVersion().equals(metadata.version()))
                .filter(metadata -> metadataScope(metadata.section()) == selectedScope.scope())
                .map(metadata -> "version-ref: "
                        + node.packageId()
                        + " -> "
                        + node.selectedVersion()
                        + " from [versions]."
                        + metadata.versionRef())
                .distinct()
                .sorted()
                .toList();
    }

    private static DependencyScope metadataScope(String section) {
        return switch (section) {
            case "api.dependencies", "dependencies" -> DependencyScope.COMPILE;
            case "runtime.dependencies" -> DependencyScope.RUNTIME;
            case "provided.dependencies" -> DependencyScope.PROVIDED;
            case "dev.dependencies" -> DependencyScope.DEV;
            case "test.dependencies" -> DependencyScope.TEST;
            case "annotationProcessors" -> DependencyScope.PROCESSOR;
            case "test.annotationProcessors" -> DependencyScope.TEST_PROCESSOR;
            default -> null;
        };
    }
}
