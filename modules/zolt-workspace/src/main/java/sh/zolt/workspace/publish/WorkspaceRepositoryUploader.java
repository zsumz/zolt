package sh.zolt.workspace.publish;

import sh.zolt.maven.ArtifactDescriptor;
import sh.zolt.maven.Coordinate;
import sh.zolt.maven.repository.MavenRepositoryClient;
import sh.zolt.publish.PublishDryRunPlan;
import sh.zolt.workspace.service.Workspace;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

/**
 * Phase 2 for a plain repository: a dependency-ordered sequential upload (provider before consumer,
 * BOM last). It fails fast on the first member that cannot be uploaded and reports an exact resume
 * command listing the members that did not upload, so a retry never re-uploads what already landed.
 *
 * <p>{@code file://} targets are written directly in Maven repository layout (each file plus its
 * {@code .md5}/{@code .sha1}/{@code .sha256}); other schemes PUT through {@link MavenRepositoryClient}.
 */
public final class WorkspaceRepositoryUploader {
    private final MavenRepositoryClient repositoryClient;

    public WorkspaceRepositoryUploader() {
        this(new MavenRepositoryClient());
    }

    public WorkspaceRepositoryUploader(MavenRepositoryClient repositoryClient) {
        this.repositoryClient = repositoryClient;
    }

    WorkspacePublishReport upload(
            Workspace workspace,
            List<WorkspacePublishReport.Member> members,
            WorkspacePublishService.Options options) {
        for (int index = 0; index < members.size(); index++) {
            WorkspacePublishReport.Member member = members.get(index);
            Path memberRoot = workspace.root().resolve(member.memberPath());
            try {
                uploadMember(memberRoot, member.plan());
            } catch (RuntimeException | IOException exception) {
                List<String> remaining = new ArrayList<>();
                for (WorkspacePublishReport.Member pending : members.subList(index, members.size())) {
                    remaining.add("--member " + pending.memberPath());
                }
                String resume = "zolt publish --workspace " + String.join(" ", remaining);
                return new WorkspacePublishReport(
                        members,
                        List.of("upload failed for " + member.coordinate() + ": " + exception.getMessage()),
                        false,
                        Optional.empty(),
                        Optional.of(resume));
            }
        }
        return new WorkspacePublishReport(members, List.of(), true, Optional.empty(), Optional.empty());
    }

    private void uploadMember(Path memberRoot, PublishDryRunPlan plan) throws IOException {
        String url = plan.repositoryUrl();
        Path pomFile = memberRoot.resolve(plan.pomPath()).normalize();
        if (isFileRepository(url)) {
            Path repositoryDirectory = Paths.get(URI.create(normalizedFileUrl(url)));
            if (!plan.pomOnly()) {
                writeFile(repositoryDirectory, plan.artifactUploadPath(), memberRoot.resolve(plan.artifactPath()));
            }
            writeFile(repositoryDirectory, plan.pomUploadPath(), pomFile);
            return;
        }
        URI repositoryUri = URI.create(url);
        Coordinate coordinate = coordinate(plan.coordinate());
        if (!plan.pomOnly()) {
            Path artifactFile = memberRoot.resolve(plan.artifactPath()).normalize();
            repositoryClient.uploadArtifact(
                    repositoryUri, new ArtifactDescriptor(coordinate, Optional.empty(), "jar"), artifactFile);
            uploadHttpChecksums(repositoryUri, plan.artifactUploadPath(), artifactFile);
        }
        repositoryClient.uploadPom(repositoryUri, coordinate, pomFile);
        uploadHttpChecksums(repositoryUri, plan.pomUploadPath(), pomFile);
    }

    private void uploadHttpChecksums(URI repositoryUri, String uploadPath, Path file) throws IOException {
        for (Checksum checksum : checksums(file)) {
            Path sidecar = Files.createTempFile("zolt-checksum", "." + checksum.extension());
            Files.writeString(sidecar, checksum.value());
            repositoryClient.uploadFile(repositoryUri, uploadPath + "." + checksum.extension(), sidecar, Optional.empty());
            Files.deleteIfExists(sidecar);
        }
    }

    private static void writeFile(Path repositoryDirectory, String uploadPath, Path source) throws IOException {
        Path target = repositoryDirectory.resolve(uploadPath).normalize();
        Files.createDirectories(target.getParent());
        Files.copy(source, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        for (Checksum checksum : checksums(source)) {
            Files.writeString(
                    repositoryDirectory.resolve(uploadPath + "." + checksum.extension()).normalize(),
                    checksum.value());
        }
    }

    private static List<Checksum> checksums(Path file) throws IOException {
        byte[] bytes = Files.readAllBytes(file);
        return List.of(
                new Checksum("md5", digest("MD5", bytes)),
                new Checksum("sha1", digest("SHA-1", bytes)),
                new Checksum("sha256", digest("SHA-256", bytes)));
    }

    private static String digest(String algorithm, byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance(algorithm).digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(algorithm + " is unavailable", exception);
        }
    }

    private static boolean isFileRepository(String url) {
        return url.startsWith("file:");
    }

    private static String normalizedFileUrl(String url) {
        // Accept file:/path and file:///path; URI.create needs an authority-less absolute form.
        if (url.startsWith("file:///") || url.startsWith("file://localhost/")) {
            return url;
        }
        if (url.startsWith("file://")) {
            return "file://" + url.substring("file://".length());
        }
        return "file://" + url.substring("file:".length());
    }

    private static Coordinate coordinate(String coordinate) {
        String[] parts = coordinate.split(":", -1);
        return new Coordinate(parts[0], parts[1], Optional.of(parts[2]));
    }

    private record Checksum(String extension, String value) {
    }
}
