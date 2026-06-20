package com.zolt.resolve;

import com.zolt.maven.RawPomDependency;
import java.util.Comparator;
import java.util.List;

final class DependencyTraversalOrdering {
    private DependencyTraversalOrdering() {
    }

    static List<DependencyPolicyEffect> sortedPolicyEffects(List<DependencyPolicyEffect> policyEffects) {
        return policyEffects.stream()
                .distinct()
                .sorted(Comparator.comparing(policyEffect -> policyEffect.kind()
                        + ":"
                        + policyEffect.packageId()
                        + ":"
                        + policyEffect.requestedVersion().orElse("")
                        + ":"
                        + policyEffect.source().orElse("")
                        + ":"
                        + policyEffect.policy()))
                .toList();
    }

    static String requestSortKey(DependencyRequest request) {
        return request.packageId() + ":" + request.requestedVersion() + ":" + request.scope();
    }

    static String dependencySortKey(NormalizedDependency dependency) {
        RawPomDependency raw = dependency.rawDependency();
        return raw.groupId()
                + ":"
                + raw.artifactId()
                + ":"
                + raw.version().orElse("")
                + ":"
                + dependency.scope();
    }
}
