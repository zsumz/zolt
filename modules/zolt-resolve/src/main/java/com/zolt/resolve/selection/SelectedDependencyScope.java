package com.zolt.resolve.selection;

import com.zolt.dependency.DependencyScope;
import com.zolt.maven.ArtifactDescriptor;
import java.util.Optional;

public record SelectedDependencyScope(
        DependencyScope scope,
        boolean direct,
        Optional<ArtifactDescriptor> artifactDescriptor) {
    public SelectedDependencyScope(DependencyScope scope, boolean direct) {
        this(scope, direct, Optional.empty());
    }

    public SelectedDependencyScope {
        artifactDescriptor = artifactDescriptor == null ? Optional.empty() : artifactDescriptor;
    }

    SelectedDependencyScope merge(SelectedDependencyScope other) {
        return new SelectedDependencyScope(
                scope,
                direct || other.direct,
                artifactDescriptor.isPresent() ? artifactDescriptor : other.artifactDescriptor);
    }
}
