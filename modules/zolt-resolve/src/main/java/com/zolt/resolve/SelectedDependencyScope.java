package com.zolt.resolve;

import com.zolt.dependency.DependencyScope;
import com.zolt.maven.ArtifactDescriptor;
import java.util.Optional;

record SelectedDependencyScope(
        DependencyScope scope,
        boolean direct,
        Optional<ArtifactDescriptor> artifactDescriptor) {
    SelectedDependencyScope(DependencyScope scope, boolean direct) {
        this(scope, direct, Optional.empty());
    }

    SelectedDependencyScope {
        artifactDescriptor = artifactDescriptor == null ? Optional.empty() : artifactDescriptor;
    }

    SelectedDependencyScope merge(SelectedDependencyScope other) {
        return new SelectedDependencyScope(
                scope,
                direct || other.direct,
                artifactDescriptor.isPresent() ? artifactDescriptor : other.artifactDescriptor);
    }
}
