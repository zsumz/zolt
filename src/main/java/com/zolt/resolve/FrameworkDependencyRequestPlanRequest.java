package com.zolt.resolve;

import com.zolt.dependency.PackageId;
import com.zolt.maven.Coordinate;
import com.zolt.project.ProjectConfig;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public record FrameworkDependencyRequestPlanRequest(
        ProjectConfig config,
        List<FrameworkDependencyCandidate> candidates,
        Map<PackageId, String> selectedVersions,
        Map<PackageId, String> managedVersions,
        ArtifactPathResolver artifactPathResolver,
        Supplier<List<DependencyRequest>> platformPropertiesRequests) {
    public FrameworkDependencyRequestPlanRequest {
        if (config == null) {
            throw new IllegalArgumentException("Framework dependency request planning requires project config.");
        }
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
        selectedVersions = selectedVersions == null ? Map.of() : Map.copyOf(selectedVersions);
        managedVersions = managedVersions == null ? Map.of() : Map.copyOf(managedVersions);
        if (artifactPathResolver == null) {
            throw new IllegalArgumentException("Framework dependency request planning requires artifact path resolution.");
        }
        platformPropertiesRequests = platformPropertiesRequests == null ? List::of : platformPropertiesRequests;
    }

    @FunctionalInterface
    public interface ArtifactPathResolver {
        Path jarPath(Coordinate coordinate);
    }
}
