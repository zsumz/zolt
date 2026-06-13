package com.zolt.resolve;

import java.util.List;

public record FrameworkDependencyCandidate(
        PackageId packageId,
        String selectedVersion,
        List<DependencyScope> selectedScopes) {
    public FrameworkDependencyCandidate {
        selectedScopes = selectedScopes == null ? List.of() : List.copyOf(selectedScopes);
    }

    public boolean entersPackagedMainRuntimeClasspath() {
        return selectedScopes.stream()
                .anyMatch(scope -> scope.entersMainRuntimeClasspath() && scope.packagedByDefault());
    }
}
