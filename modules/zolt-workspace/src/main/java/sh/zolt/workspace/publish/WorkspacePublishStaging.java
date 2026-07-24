package sh.zolt.workspace.publish;

import sh.zolt.maven.repository.RepositoryAuthentication;
import sh.zolt.project.RepositoryUrlPolicy;
import sh.zolt.publish.PublishArtifactPlan;
import sh.zolt.publish.PublishDryRunPlan;
import sh.zolt.publish.PublishException;
import sh.zolt.publish.PublishRepositoryAuthentication;
import sh.zolt.publish.PublishRepositorySettings;
import sh.zolt.publish.PublishSigner;
import sh.zolt.publish.PublishSigningSettings;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Phase-1 materialization of the ENTIRE plain-repository upload set, before the first request. It
 * resolves and validates every member's repository target under the same URL policy Phase 2 uses
 * (the separate local {@code file:} route included), resolves every repository credential, runs the
 * signing {@link PublishSigner#preflight() preflight} once per distinct signing configuration, and
 * generates every checksum and detached signature into a staging directory. The result is a family of
 * {@link StagedMember}s Phase 2 uploads as pure pre-staged bytes — so a mid-member signing or
 * digesting failure is structurally impossible once uploads begin. Any violation is a Phase-1 blocker
 * and nothing is uploaded.
 */
final class WorkspacePublishStaging {
    private static final List<Algorithm> ALGORITHMS = List.of(
            new Algorithm("md5", "MD5"), new Algorithm("sha1", "SHA-1"), new Algorithm("sha256", "SHA-256"));

    private final Function<String, String> environment;

    WorkspacePublishStaging() {
        this(System::getenv);
    }

    WorkspacePublishStaging(Function<String, String> environment) {
        this.environment = environment;
    }

    Preparation materialize(
            List<MemberPublication> members, Path stagingRoot, WorkspacePublishService.Options options) {
        List<String> blockers = new ArrayList<>();
        List<Resolved> resolved = new ArrayList<>();
        for (MemberPublication member : members) {
            RepositoryTarget target = resolveTarget(member, blockers);
            if (target != null) {
                resolved.add(new Resolved(member, target));
            }
        }
        // A repository-URL or credential violation blocks the family before any signing or staging work.
        if (!blockers.isEmpty()) {
            return new Preparation(List.of(), blockers);
        }
        // Signing preflight runs before a single artifact is staged: a missing gpg, an unusable key, or
        // an unset passphrase must fail here, not mid-upload with a member half-published.
        for (PublishSigningSettings signing : distinctSigning(members)) {
            try {
                new PublishSigner(signing, environment).preflight();
            } catch (PublishException exception) {
                blockers.add("signing preflight failed — " + exception.getMessage());
            }
        }
        if (!blockers.isEmpty()) {
            return new Preparation(List.of(), blockers);
        }
        List<StagedMember> staged = new ArrayList<>();
        for (Resolved entry : resolved) {
            try {
                staged.add(stageMember(entry, stagingRoot));
            } catch (IOException exception) {
                blockers.add(entry.member().coordinate() + ": could not stage its upload set — " + exception.getMessage());
            }
        }
        if (!blockers.isEmpty()) {
            return new Preparation(List.of(), blockers);
        }
        return new Preparation(List.copyOf(staged), List.of());
    }

    private RepositoryTarget resolveTarget(MemberPublication member, List<String> blockers) {
        PublishDryRunPlan plan = member.plan();
        PublishRepositorySettings repository = member.publish().repositories().get(plan.repositoryId());
        String url = repository != null ? repository.url() : plan.repositoryUrl();
        if (url.startsWith("file:")) {
            try {
                return RepositoryTarget.local(Paths.get(URI.create(normalizedFileUrl(url))));
            } catch (RuntimeException exception) {
                blockers.add(member.coordinate() + ": invalid file repository URL `" + url + "` — "
                        + exception.getMessage());
                return null;
            }
        }
        try {
            Optional<RepositoryAuthentication> authentication = repository != null
                    ? PublishRepositoryAuthentication.resolve(repository, member.repositoryCredentials(), environment)
                    : RepositoryAuthentication.none();
            URI uri = RepositoryUrlPolicy.requireSafeUrl(
                    "Publish repository `" + plan.repositoryId() + "`", url, authentication.isPresent());
            return RepositoryTarget.remote(uri, authentication);
        } catch (IllegalArgumentException | PublishException exception) {
            blockers.add(member.coordinate() + ": " + exception.getMessage());
            return null;
        }
    }

    private static Set<PublishSigningSettings> distinctSigning(List<MemberPublication> members) {
        Set<PublishSigningSettings> signing = new LinkedHashSet<>();
        for (MemberPublication member : members) {
            if (member.publish().signing().enabled()) {
                signing.add(member.publish().signing());
            }
        }
        return signing;
    }

    private StagedMember stageMember(Resolved entry, Path stagingRoot) throws IOException {
        MemberPublication member = entry.member();
        PublishDryRunPlan plan = member.plan();
        PublishSigner signer =
                member.publish().signing().enabled() ? new PublishSigner(member.publish().signing(), environment) : null;
        Path memberRoot = member.memberRoot();
        List<StagedArtifact> artifacts = new ArrayList<>();
        if (!plan.pomOnly()) {
            stageFile(memberRoot.resolve(plan.artifactPath()).normalize(), plan.artifactUploadPath(),
                    signer, stagingRoot, artifacts);
        }
        for (PublishArtifactPlan supplemental : plan.supplementalArtifacts()) {
            stageFile(memberRoot.resolve(supplemental.path()).normalize(), supplemental.uploadPath(),
                    signer, stagingRoot, artifacts);
        }
        stageFile(memberRoot.resolve(plan.pomPath()).normalize(), plan.pomUploadPath(), signer, stagingRoot, artifacts);
        return new StagedMember(member.toReportMember(), entry.target(), List.copyOf(artifacts));
    }

    /**
     * Stages one source file and its derived upload files in Maven upload order: the file itself, its
     * three checksum sidecars, and — when signing — its detached signature followed by that
     * signature's own checksums. Signatures are generated here (Phase 1); Phase 2 only transfers bytes.
     */
    private void stageFile(Path source, String uploadPath, PublishSigner signer, Path stagingRoot,
            List<StagedArtifact> out) throws IOException {
        out.add(new StagedArtifact(uploadPath, source));
        stageChecksums(source, uploadPath, stagingRoot, out);
        if (signer != null) {
            Path signature = signer.sign(source);
            Path stagedSignature = stagingRoot.resolve(uploadPath + ".asc");
            Files.createDirectories(stagedSignature.getParent());
            Files.move(signature, stagedSignature, StandardCopyOption.REPLACE_EXISTING);
            out.add(new StagedArtifact(uploadPath + ".asc", stagedSignature));
            stageChecksums(stagedSignature, uploadPath + ".asc", stagingRoot, out);
        }
    }

    private static void stageChecksums(Path source, String uploadPath, Path stagingRoot, List<StagedArtifact> out)
            throws IOException {
        byte[] bytes = Files.readAllBytes(source);
        for (Algorithm algorithm : ALGORITHMS) {
            String path = uploadPath + "." + algorithm.extension();
            Path staged = stagingRoot.resolve(path);
            Files.createDirectories(staged.getParent());
            Files.writeString(staged, digest(algorithm.jcaName(), bytes));
            out.add(new StagedArtifact(path, staged));
        }
    }

    private static String digest(String algorithm, byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance(algorithm).digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(algorithm + " is unavailable", exception);
        }
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

    /** A member paired with its resolved, policy-validated repository target. */
    private record Resolved(MemberPublication member, RepositoryTarget target) {
    }

    /**
     * The materialized family plus any Phase-1 blockers. When {@code blockers} is non-empty,
     * {@code members} is empty and nothing is uploaded.
     */
    record Preparation(List<StagedMember> members, List<String> blockers) {
    }

    private record Algorithm(String extension, String jcaName) {
    }
}
