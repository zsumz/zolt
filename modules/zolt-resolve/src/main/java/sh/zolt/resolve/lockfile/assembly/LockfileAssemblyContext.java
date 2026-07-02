package sh.zolt.resolve.lockfile.assembly;

import sh.zolt.cache.CachedArtifact;
import sh.zolt.dependency.PackageId;
import sh.zolt.maven.ArtifactDescriptor;
import sh.zolt.maven.Coordinate;
import sh.zolt.project.ProjectConfig;
import sh.zolt.resolve.metadata.platform.ManagedVersion;
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
