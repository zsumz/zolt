package sh.zolt.publish;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Plans the checksum sidecars listed in {@code publish --dry-run}: for the main artifact, every
 * supplemental artifact, and the POM it records the {@code .md5}/{@code .sha1}/{@code .sha256}
 * upload paths and digest values. Files that do not yet exist are skipped (a missing-artifact
 * blocker is reported elsewhere).
 */
final class PublishChecksumSidecarPlanner {
    private PublishChecksumSidecarPlanner() {
    }

    static List<PublishChecksumSidecar> plan(
            Path root,
            Path artifactPath,
            String artifactUploadPath,
            List<PublishArtifactPlan> supplementalArtifacts,
            Path pomPath,
            String pomUploadPath) {
        List<PublishChecksumSidecar> sidecars = new ArrayList<>();
        addSidecars(sidecars, "artifact", artifactPath, artifactUploadPath);
        for (PublishArtifactPlan supplemental : supplementalArtifacts) {
            addSidecars(
                    sidecars,
                    supplemental.id(),
                    root.resolve(supplemental.path()).normalize(),
                    supplemental.uploadPath());
        }
        addSidecars(sidecars, "pom", pomPath, pomUploadPath);
        return List.copyOf(sidecars);
    }

    private static void addSidecars(
            List<PublishChecksumSidecar> sidecars,
            String subject,
            Path file,
            String uploadPath) {
        if (!Files.isRegularFile(file)) {
            return;
        }
        for (PublishChecksum.Sidecar sidecar : PublishChecksum.sidecars(file)) {
            sidecars.add(new PublishChecksumSidecar(
                    subject,
                    sidecar.extension(),
                    uploadPath + "." + sidecar.extension(),
                    sidecar.value()));
        }
    }
}
