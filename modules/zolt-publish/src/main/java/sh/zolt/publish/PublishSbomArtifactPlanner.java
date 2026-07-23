package sh.zolt.publish;

import java.nio.file.Path;
import java.util.Optional;
import sh.zolt.maven.ArtifactDescriptor;
import sh.zolt.maven.Coordinate;
import sh.zolt.maven.repository.MavenRepositoryPathBuilder;

/**
 * Plans the CycloneDX SBOM supplemental artifact (classifier {@code cyclonedx}, extension
 * {@code json}) for {@code zolt publish --sbom}. It produces an ordinary {@link PublishArtifactPlan}
 * so the SBOM rides the existing checksum + signing planner uniformly.
 */
final class PublishSbomArtifactPlanner {
    private PublishSbomArtifactPlanner() {
    }

    static PublishArtifactPlan plan(
            Path root, Coordinate coordinate, Path sbomFile, MavenRepositoryPathBuilder pathBuilder) {
        String uploadPath = pathBuilder.artifactPath(
                new ArtifactDescriptor(coordinate, Optional.of("cyclonedx"), "json"));
        return new PublishArtifactPlan(
                "cyclonedx",
                Optional.of("cyclonedx"),
                PublishDryRunArtifactEvidencePlanner.display(root, sbomFile),
                PublishChecksum.sha256(sbomFile),
                uploadPath);
    }
}
