package sh.zolt.workspace.publish;

import sh.zolt.maven.repository.MavenRepositoryClient;
import sh.zolt.maven.repository.RepositoryAuthentication;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Phase 2 for a plain repository: a dependency-ordered sequential upload (provider before consumer,
 * BOM last) of a family already staged in Phase 1. Every byte — artifact, POM, checksum, and detached
 * signature — is pre-generated, so a member cannot fail mid-way on signing or digesting; only the
 * transfer itself can fail.
 *
 * <p>Each file is uploaded IDEMPOTENTLY: the repository is queried first (a remote GET, or a local
 * file existence + content check) and a path that already holds the SAME bytes is skipped, so a
 * resumed publish re-PUTs nothing an immutable release repository would reject. A path that already
 * holds DIFFERENT bytes is a hard, non-resumable conflict. Any other transfer failure fails fast and
 * reports an exact resume command naming the member that failed and those after it — never the
 * providers that already landed.
 */
public final class WorkspaceRepositoryUploader {
    private final MavenRepositoryClient repositoryClient;

    public WorkspaceRepositoryUploader() {
        this(new MavenRepositoryClient());
    }

    public WorkspaceRepositoryUploader(MavenRepositoryClient repositoryClient) {
        this.repositoryClient = repositoryClient;
    }

    WorkspacePublishReport upload(List<StagedMember> members, WorkspacePublishService.Options options) {
        List<WorkspacePublishReport.Member> reportMembers = new ArrayList<>();
        for (StagedMember member : members) {
            reportMembers.add(member.reportMember());
        }
        for (int index = 0; index < members.size(); index++) {
            StagedMember member = members.get(index);
            try {
                uploadMember(member);
            } catch (MismatchedArtifactException conflict) {
                // An occupied release path with different content: resuming cannot fix it, so no resume
                // command — the operator must resolve the conflict (bump the version or clear the path).
                return new WorkspacePublishReport(
                        reportMembers, List.of(conflict.getMessage()), false, Optional.empty(), Optional.empty());
            } catch (RuntimeException | IOException exception) {
                // Resume the failed member and everything after it, selected EXACTLY (never a
                // dependency-expanded, already-uploaded provider), carrying the original semantic options.
                List<String> remaining = new ArrayList<>();
                for (StagedMember pending : members.subList(index, members.size())) {
                    remaining.add(pending.memberPath());
                }
                return new WorkspacePublishReport(
                        reportMembers,
                        List.of("upload failed for " + member.coordinate() + ": " + exception.getMessage()),
                        false,
                        Optional.empty(),
                        Optional.of(options.resumeCommand(remaining)));
            }
        }
        return new WorkspacePublishReport(reportMembers, List.of(), true, Optional.empty(), Optional.empty());
    }

    private void uploadMember(StagedMember member) throws IOException {
        RepositoryTarget target = member.target();
        for (StagedArtifact artifact : member.artifacts()) {
            if (target.local()) {
                uploadToFile(target.directory(), artifact);
            } else {
                uploadToHttp(target.uri(), target.authentication(), artifact);
            }
        }
    }

    private void uploadToHttp(URI uri, Optional<RepositoryAuthentication> authentication, StagedArtifact artifact)
            throws IOException {
        byte[] local = Files.readAllBytes(artifact.source());
        Optional<byte[]> remote = repositoryClient.fetchFile(uri, artifact.repositoryPath(), authentication);
        if (remote.isPresent()) {
            requireSameContent(artifact.repositoryPath(), local, remote.orElseThrow());
            return;
        }
        repositoryClient.uploadFile(uri, artifact.repositoryPath(), artifact.source(), authentication);
    }

    private static void uploadToFile(Path repositoryDirectory, StagedArtifact artifact) throws IOException {
        Path target = repositoryDirectory.resolve(artifact.repositoryPath()).normalize();
        if (Files.exists(target)) {
            requireSameContent(artifact.repositoryPath(), Files.readAllBytes(artifact.source()), Files.readAllBytes(target));
            return;
        }
        Files.createDirectories(target.getParent());
        Files.copy(artifact.source(), target);
    }

    private static void requireSameContent(String repositoryPath, byte[] local, byte[] remote) {
        if (!Arrays.equals(local, remote)) {
            throw new MismatchedArtifactException(repositoryPath);
        }
    }

    /** An existing release path already holds different bytes than this publish would upload. */
    private static final class MismatchedArtifactException extends RuntimeException {
        MismatchedArtifactException(String repositoryPath) {
            super("release path `" + repositoryPath + "` already holds different content than this publish would "
                    + "upload; a release repository will not overwrite it. Next: bump the version or remove the "
                    + "stale artifact, then re-publish.");
        }
    }
}
