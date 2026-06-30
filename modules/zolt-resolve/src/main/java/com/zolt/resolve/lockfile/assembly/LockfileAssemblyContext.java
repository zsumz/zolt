package com.zolt.resolve.lockfile.assembly;

import com.zolt.cache.CachedArtifact;
import com.zolt.dependency.PackageId;
import com.zolt.maven.ArtifactDescriptor;
import com.zolt.maven.Coordinate;
import com.zolt.project.ProjectConfig;
import com.zolt.resolve.metadata.platform.ManagedVersion;
import java.util.List;
import java.util.Map;

public interface LockfileAssemblyContext {
    ProjectConfig config();

    Map<ArtifactDescriptor, CachedArtifact> getArtifacts(List<ArtifactDescriptor> descriptors);

    CachedArtifact getPom(Coordinate coordinate);

    String sourceFor(CachedArtifact artifact);

    Map<PackageId, ManagedVersion> projectManagedVersionDetails();

    void addLockfileAssemblyNanos(long nanos);
}
