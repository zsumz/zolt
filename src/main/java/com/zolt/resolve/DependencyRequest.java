package com.zolt.resolve;

import com.zolt.maven.ArtifactDescriptor;
import java.util.List;
import java.util.Optional;

public record DependencyRequest(
        PackageId packageId,
        String requestedVersion,
        DependencyScope scope,
        RequestOrigin origin,
        Optional<ArtifactDescriptor> artifactDescriptor,
        List<DependencyExclusion> exclusions) {
    public DependencyRequest(
            PackageId packageId,
            String requestedVersion,
            DependencyScope scope,
            RequestOrigin origin,
            Optional<ArtifactDescriptor> artifactDescriptor) {
        this(packageId, requestedVersion, scope, origin, artifactDescriptor, List.of());
    }

    public DependencyRequest(
            PackageId packageId,
            String requestedVersion,
            DependencyScope scope,
            RequestOrigin origin) {
        this(packageId, requestedVersion, scope, origin, Optional.empty(), List.of());
    }

    public DependencyRequest(
            PackageId packageId,
            String requestedVersion,
            DependencyScope scope,
            RequestOrigin origin,
            List<DependencyExclusion> exclusions) {
        this(packageId, requestedVersion, scope, origin, Optional.empty(), exclusions);
    }

    public DependencyRequest {
        artifactDescriptor = artifactDescriptor == null ? Optional.empty() : artifactDescriptor;
        exclusions = exclusions == null ? List.of() : List.copyOf(exclusions);
    }

    public boolean direct() {
        return origin == RequestOrigin.DIRECT;
    }
}
