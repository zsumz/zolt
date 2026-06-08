package com.zolt.resolve;

import com.zolt.maven.ArtifactDescriptor;
import java.util.Optional;

public record DependencyRequest(
        PackageId packageId,
        String requestedVersion,
        DependencyScope scope,
        RequestOrigin origin,
        Optional<ArtifactDescriptor> artifactDescriptor) {
    public DependencyRequest(
            PackageId packageId,
            String requestedVersion,
            DependencyScope scope,
            RequestOrigin origin) {
        this(packageId, requestedVersion, scope, origin, Optional.empty());
    }

    public DependencyRequest {
        artifactDescriptor = artifactDescriptor == null ? Optional.empty() : artifactDescriptor;
    }

    public boolean direct() {
        return origin == RequestOrigin.DIRECT;
    }
}
