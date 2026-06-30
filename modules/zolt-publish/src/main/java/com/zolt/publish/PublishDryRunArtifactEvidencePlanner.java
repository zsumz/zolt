package com.zolt.publish;

import com.zolt.build.packageevidence.PackageEvidenceArtifact;
import com.zolt.build.packageevidence.PackageEvidenceManifest;
import com.zolt.build.packageevidence.PackageEvidenceManifestReader;
import com.zolt.build.PackageException;
import com.zolt.maven.ArtifactDescriptor;
import com.zolt.maven.Coordinate;
import com.zolt.maven.repository.MavenRepositoryPathBuilder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

final class PublishDryRunArtifactEvidencePlanner {
    private final PackageEvidenceManifestReader evidenceManifestReader;
    private final MavenRepositoryPathBuilder repositoryPathBuilder;

    PublishDryRunArtifactEvidencePlanner(
            PackageEvidenceManifestReader evidenceManifestReader,
            MavenRepositoryPathBuilder repositoryPathBuilder) {
        this.evidenceManifestReader = evidenceManifestReader;
        this.repositoryPathBuilder = repositoryPathBuilder;
    }

    PublishDryRunArtifactEvidence plan(
            Path root,
            Coordinate coordinate,
            Path artifactPath,
            Path evidencePath,
            List<String> blockers) {
        if (!Files.isRegularFile(artifactPath)) {
            blockers.add("missing artifact: run `zolt package` to create " + displayPath(root, artifactPath));
            return empty();
        }
        if (!Files.isRegularFile(evidencePath)) {
            blockers.add("missing package evidence: run `zolt package` to create " + displayPath(root, evidencePath));
            return empty();
        }
        try {
            PackageEvidenceManifest evidence = evidenceManifestReader.read(evidencePath);
            Path evidenceArchive = root.resolve(evidence.archive()).normalize();
            if (!evidenceArchive.equals(artifactPath)) {
                blockers.add("package evidence archive mismatch: "
                        + displayPath(root, evidencePath)
                        + " describes "
                        + displayPath(root, evidenceArchive)
                        + " but publish selected "
                        + displayPath(root, artifactPath)
                        + ". Run `zolt package` to refresh package evidence.");
            }
            String actualSha256 = PublishChecksum.sha256(artifactPath);
            if (!actualSha256.equals(evidence.archiveSha256())) {
                blockers.add("stale package evidence: run `zolt package` to refresh "
                        + displayPath(root, evidencePath));
            }
            return new PublishDryRunArtifactEvidence(
                    evidence.archiveSha256(),
                    supplementalArtifacts(root, coordinate, evidence, evidencePath, blockers));
        } catch (PackageException exception) {
            blockers.add("invalid package evidence: " + exception.getMessage());
            return empty();
        }
    }

    private List<PublishArtifactPlan> supplementalArtifacts(
            Path root,
            Coordinate coordinate,
            PackageEvidenceManifest evidence,
            Path evidencePath,
            List<String> blockers) {
        List<PublishArtifactPlan> artifacts = new ArrayList<>();
        for (PackageEvidenceArtifact artifact : evidence.artifacts()) {
            if ("main".equals(artifact.classifier())) {
                continue;
            }
            Path artifactPath = root.resolve(artifact.path()).normalize();
            String uploadPath = repositoryPathBuilder.artifactPath(new ArtifactDescriptor(
                    coordinate,
                    Optional.of(artifact.classifier()),
                    extension(artifactPath)));
            if (!Files.isRegularFile(artifactPath)) {
                blockers.add("missing supplemental artifact: run `zolt package` to create "
                        + displayPath(root, artifactPath));
                continue;
            }
            String actualSha256 = PublishChecksum.sha256(artifactPath);
            if (!actualSha256.equals(artifact.sha256())) {
                blockers.add("stale supplemental package evidence: run `zolt package` to refresh "
                        + displayPath(root, evidencePath));
            }
            artifacts.add(new PublishArtifactPlan(
                    artifact.classifier(),
                    Optional.of(artifact.classifier()),
                    display(root, artifactPath),
                    artifact.sha256(),
                    uploadPath));
        }
        return List.copyOf(artifacts);
    }

    static String extension(Path artifactPath) {
        String fileName = artifactPath.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            throw new PublishException("Could not determine publish artifact extension from `" + fileName + "`.");
        }
        return fileName.substring(dot + 1);
    }

    static Path display(Path root, Path path) {
        return Path.of(displayPath(root, path));
    }

    static String displayPath(Path root, Path path) {
        Path normalized = path.toAbsolutePath().normalize();
        if (normalized.startsWith(root)) {
            return root.relativize(normalized).toString();
        }
        return normalized.toString();
    }

    private static PublishDryRunArtifactEvidence empty() {
        return new PublishDryRunArtifactEvidence("", List.of());
    }
}
