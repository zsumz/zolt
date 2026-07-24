package sh.zolt.workspace.publish;

import sh.zolt.maven.ArtifactDescriptor;
import sh.zolt.maven.Coordinate;
import sh.zolt.maven.repository.MavenRepositoryClient;
import sh.zolt.maven.repository.RepositoryAuthentication;
import sh.zolt.project.RepositoryUrlPolicy;
import sh.zolt.publish.PublishArtifactPlan;
import sh.zolt.publish.PublishDryRunPlan;
import sh.zolt.publish.PublishRepositoryAuthentication;
import sh.zolt.publish.PublishRepositorySettings;
import sh.zolt.publish.PublishSettings;
import sh.zolt.publish.PublishSigner;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Phase 2 for a plain repository: a dependency-ordered sequential upload (provider before consumer,
 * BOM last). Each member uploads its main artifact, every supplemental (sources/javadoc/CycloneDX),
 * and the POM — each with its {@code .md5}/{@code .sha1}/{@code .sha256} checksums and, when signing
 * is enabled, a {@code .asc} detached signature (with checksums of the signature) — mirroring the
 * single-project uploader. It fails fast on the first member that cannot be uploaded and reports an
 * exact resume command listing the members that did not upload, so a retry never re-uploads what
 * already landed.
 *
 * <p>{@code file://} targets are written directly in Maven repository layout; every other scheme
 * PUTs through {@link MavenRepositoryClient} with the member's resolved repository credentials applied
 * to every request (a credentialed remote repository is rejected unless it is HTTPS).
 */
public final class WorkspaceRepositoryUploader {
    private final MavenRepositoryClient repositoryClient;
    private final Function<String, String> environment;

    public WorkspaceRepositoryUploader() {
        this(new MavenRepositoryClient());
    }

    public WorkspaceRepositoryUploader(MavenRepositoryClient repositoryClient) {
        this(repositoryClient, System::getenv);
    }

    WorkspaceRepositoryUploader(MavenRepositoryClient repositoryClient, Function<String, String> environment) {
        this.repositoryClient = repositoryClient;
        this.environment = environment;
    }

    WorkspacePublishReport upload(List<MemberPublication> members, WorkspacePublishService.Options options) {
        List<WorkspacePublishReport.Member> reportMembers = new ArrayList<>();
        for (MemberPublication member : members) {
            reportMembers.add(member.toReportMember());
        }
        for (int index = 0; index < members.size(); index++) {
            MemberPublication member = members.get(index);
            try {
                uploadMember(member);
            } catch (RuntimeException | IOException exception) {
                // Resume the failed member and everything after it, selected EXACTLY so an
                // already-uploaded provider is never re-included, and carrying the original semantic
                // options so mixed-version and SBOM behaviour survive the retry.
                List<String> remaining = new ArrayList<>();
                for (MemberPublication pending : members.subList(index, members.size())) {
                    remaining.add(pending.memberPath());
                }
                String resume = options.resumeCommand(remaining);
                return new WorkspacePublishReport(
                        reportMembers,
                        List.of("upload failed for " + member.coordinate() + ": " + exception.getMessage()),
                        false,
                        Optional.empty(),
                        Optional.of(resume));
            }
        }
        return new WorkspacePublishReport(reportMembers, List.of(), true, Optional.empty(), Optional.empty());
    }

    private void uploadMember(MemberPublication member) throws IOException {
        PublishDryRunPlan plan = member.plan();
        PublishSettings publish = member.publish();
        PublishRepositorySettings repository = publish.repositories().get(plan.repositoryId());
        String url = repository != null ? repository.url() : plan.repositoryUrl();
        PublishSigner signer =
                publish.signing().enabled() ? new PublishSigner(publish.signing(), environment) : null;
        Path memberRoot = member.memberRoot();

        if (isFileRepository(url)) {
            uploadToFileRepository(memberRoot, plan, url, signer);
            return;
        }

        Optional<RepositoryAuthentication> authentication = repository != null
                ? PublishRepositoryAuthentication.resolve(repository, member.repositoryCredentials(), environment)
                : RepositoryAuthentication.none();
        URI repositoryUri = RepositoryUrlPolicy.requireSafeUrl(
                "Publish repository `" + plan.repositoryId() + "`", url, authentication.isPresent());
        uploadToHttpRepository(memberRoot, plan, repositoryUri, authentication, signer);
    }

    private void uploadToHttpRepository(
            Path memberRoot,
            PublishDryRunPlan plan,
            URI repositoryUri,
            Optional<RepositoryAuthentication> authentication,
            PublishSigner signer)
            throws IOException {
        Coordinate coordinate = coordinate(plan.coordinate());
        if (!plan.pomOnly()) {
            Path artifactFile = memberRoot.resolve(plan.artifactPath()).normalize();
            repositoryClient.uploadArtifact(
                    repositoryUri,
                    new ArtifactDescriptor(coordinate, Optional.empty(), extension(artifactFile)),
                    artifactFile,
                    authentication);
            uploadIntegrity(repositoryUri, plan.artifactUploadPath(), artifactFile, authentication, signer);
        }
        for (PublishArtifactPlan supplemental : plan.supplementalArtifacts()) {
            Path supplementalFile = memberRoot.resolve(supplemental.path()).normalize();
            repositoryClient.uploadArtifact(
                    repositoryUri,
                    new ArtifactDescriptor(coordinate, supplemental.classifier(), extension(supplementalFile)),
                    supplementalFile,
                    authentication);
            uploadIntegrity(repositoryUri, supplemental.uploadPath(), supplementalFile, authentication, signer);
        }
        Path pomFile = memberRoot.resolve(plan.pomPath()).normalize();
        repositoryClient.uploadPom(repositoryUri, coordinate, pomFile, authentication);
        uploadIntegrity(repositoryUri, plan.pomUploadPath(), pomFile, authentication, signer);
    }

    private void uploadIntegrity(
            URI repositoryUri,
            String uploadPath,
            Path source,
            Optional<RepositoryAuthentication> authentication,
            PublishSigner signer)
            throws IOException {
        uploadHttpChecksums(repositoryUri, uploadPath, source, authentication);
        if (signer == null) {
            return;
        }
        Path signature = signer.sign(source);
        repositoryClient.uploadFile(repositoryUri, uploadPath + ".asc", signature, authentication);
        uploadHttpChecksums(repositoryUri, uploadPath + ".asc", signature, authentication);
    }

    private void uploadHttpChecksums(
            URI repositoryUri, String uploadPath, Path file, Optional<RepositoryAuthentication> authentication)
            throws IOException {
        for (Checksum checksum : checksums(file)) {
            Path sidecar = Files.createTempFile("zolt-checksum", "." + checksum.extension());
            Files.writeString(sidecar, checksum.value());
            repositoryClient.uploadFile(
                    repositoryUri, uploadPath + "." + checksum.extension(), sidecar, authentication);
            Files.deleteIfExists(sidecar);
        }
    }

    private void uploadToFileRepository(Path memberRoot, PublishDryRunPlan plan, String url, PublishSigner signer)
            throws IOException {
        Path repositoryDirectory = Paths.get(URI.create(normalizedFileUrl(url)));
        if (!plan.pomOnly()) {
            writeFile(repositoryDirectory, plan.artifactUploadPath(), memberRoot.resolve(plan.artifactPath()).normalize(),
                    signer);
        }
        for (PublishArtifactPlan supplemental : plan.supplementalArtifacts()) {
            writeFile(repositoryDirectory, supplemental.uploadPath(), memberRoot.resolve(supplemental.path()).normalize(),
                    signer);
        }
        writeFile(repositoryDirectory, plan.pomUploadPath(), memberRoot.resolve(plan.pomPath()).normalize(), signer);
    }

    private static void writeFile(Path repositoryDirectory, String uploadPath, Path source, PublishSigner signer)
            throws IOException {
        copyWithChecksums(repositoryDirectory, uploadPath, source);
        if (signer != null) {
            Path signature = signer.sign(source);
            copyWithChecksums(repositoryDirectory, uploadPath + ".asc", signature);
        }
    }

    private static void copyWithChecksums(Path repositoryDirectory, String uploadPath, Path source) throws IOException {
        Path target = repositoryDirectory.resolve(uploadPath).normalize();
        Files.createDirectories(target.getParent());
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
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

    private static String extension(Path path) {
        String fileName = path.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            throw new IllegalStateException("Could not determine artifact extension from `" + fileName + "`.");
        }
        return fileName.substring(dot + 1);
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
