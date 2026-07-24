package sh.zolt.workspace.publish;

import sh.zolt.maven.repository.RepositoryAuthentication;
import sh.zolt.project.RepositoryUrlPolicy;
import sh.zolt.publish.PublishArtifactPlan;
import sh.zolt.publish.PublishChecksum;
import sh.zolt.publish.PublishDryRunPlan;
import sh.zolt.publish.PublishException;
import sh.zolt.publish.PublishRepositoryAuthentication;
import sh.zolt.publish.PublishRepositorySettings;
import sh.zolt.publish.PublishSigner;
import sh.zolt.publish.PublishSigningSettings;
import sh.zolt.publish.SourceDateEpoch;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
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
    private final Function<String, String> environment;

    WorkspacePublishStaging() {
        this(System::getenv);
    }

    WorkspacePublishStaging(Function<String, String> environment) {
        this.environment = environment;
    }

    Preparation materialize(
            List<MemberPublication> members, Path stagingRoot, WorkspacePublishService.Options options) {
        return materialize(members, stagingRoot, options, Optional.empty());
    }

    /**
     * Materializes the upload set. On a resume ({@code resume} present) staging is resume-aware: a
     * staged detached signature whose on-disk bytes still hash to the recorded value is reused verbatim
     * rather than re-signed, so an already-uploaded signature is never superseded by a fresh
     * (wall-clock, non-deterministic) one. A staged signature that was lost or altered is regenerated
     * only under deterministic signing ({@code SOURCE_DATE_EPOCH} + a pinned key, byte-identical by
     * contract); otherwise the member is a blocker rather than a silent divergence.
     */
    Preparation materialize(
            List<MemberPublication> members,
            Path stagingRoot,
            WorkspacePublishService.Options options,
            Optional<ResumeState> resume) {
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
                staged.add(stageMember(entry, stagingRoot, resume));
            } catch (IOException | PublishException exception) {
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

    private StagedMember stageMember(Resolved entry, Path stagingRoot, Optional<ResumeState> resume) throws IOException {
        MemberPublication member = entry.member();
        PublishDryRunPlan plan = member.plan();
        PublishSigningSettings signing = member.publish().signing();
        PublishSigner signer = signing.enabled() ? new PublishSigner(signing, environment) : null;
        boolean deterministicSigning = signing.enabled() && SourceDateEpoch.parse(environment).reproducible();
        Path memberRoot = member.memberRoot();
        List<StagedArtifact> artifacts = new ArrayList<>();
        if (!plan.pomOnly()) {
            stageFile(memberRoot.resolve(plan.artifactPath()).normalize(), plan.artifactUploadPath(),
                    signer, deterministicSigning, stagingRoot, resume, artifacts);
        }
        for (PublishArtifactPlan supplemental : plan.supplementalArtifacts()) {
            stageFile(memberRoot.resolve(supplemental.path()).normalize(), supplemental.uploadPath(),
                    signer, deterministicSigning, stagingRoot, resume, artifacts);
        }
        stageFile(memberRoot.resolve(plan.pomPath()).normalize(), plan.pomUploadPath(),
                signer, deterministicSigning, stagingRoot, resume, artifacts);
        return new StagedMember(member.toReportMember(), entry.target(), signingIdentity(signing), List.copyOf(artifacts));
    }

    /**
     * Stages one source file and its derived upload files in Maven upload order: the file itself, its
     * three checksum sidecars, and — when signing — its detached signature followed by that
     * signature's own checksums. Signatures are generated here (Phase 1); Phase 2 only transfers bytes.
     */
    private void stageFile(Path source, String uploadPath, PublishSigner signer, boolean deterministicSigning,
            Path stagingRoot, Optional<ResumeState> resume, List<StagedArtifact> out) throws IOException {
        stageContent(source, uploadPath, stagingRoot, out);
        if (signer != null) {
            String signaturePath = uploadPath + ".asc";
            Path stagedSignature = resumableSignature(
                    source, signaturePath, signer, deterministicSigning, stagingRoot, resume);
            stageContent(stagedSignature, signaturePath, stagingRoot, out);
        }
    }

    /**
     * The detached signature for {@code source}, reused verbatim on a resume when the on-disk staged
     * signature still hashes to the recorded value (so an already-uploaded signature is never
     * superseded). A lost or altered staged signature is regenerated only under deterministic signing;
     * otherwise it cannot be reproduced byte-for-byte, so the member fails actionably rather than
     * diverging.
     */
    private Path resumableSignature(Path source, String signaturePath, PublishSigner signer,
            boolean deterministicSigning, Path stagingRoot, Optional<ResumeState> resume) throws IOException {
        Path stagedSignature = stagingRoot.resolve(signaturePath);
        Optional<String> recorded = resume.flatMap(state -> state.recordedHash(signaturePath));
        if (recorded.isPresent()) {
            if (Files.isRegularFile(stagedSignature) && Sha256.hex(stagedSignature).equals(recorded.orElseThrow())) {
                return stagedSignature; // intact from the interrupted publish — reuse its exact bytes
            }
            if (!deterministicSigning) {
                throw new PublishException("cannot resume a signed publish whose staged signature for `"
                        + signaturePath + "` was lost or changed and cannot be reproduced byte-for-byte; re-run the "
                        + "full publish or set SOURCE_DATE_EPOCH (with a pinned [publish.signing].keyId) to sign "
                        + "reproducibly.");
            }
        }
        Path signature = signer.sign(source);
        Files.createDirectories(stagedSignature.getParent());
        Files.move(signature, stagedSignature, StandardCopyOption.REPLACE_EXISTING);
        return stagedSignature;
    }

    /**
     * Stages a content file (a primary/supplemental/POM, or a detached signature) followed by its three
     * checksum sidecars, each carrying the SHA-256 of its exact staged bytes. The content file is
     * streamed once through every algorithm (no full-file buffer), so a multi-MB primary is never read
     * whole; its sha256 sidecar value is that file's staged-bytes digest.
     */
    private static void stageContent(Path source, String uploadPath, Path stagingRoot, List<StagedArtifact> out)
            throws IOException {
        List<PublishChecksum.Sidecar> sidecars = PublishChecksum.sidecars(source);
        out.add(new StagedArtifact(uploadPath, source, sidecarValue(sidecars, "sha256")));
        for (PublishChecksum.Sidecar sidecar : sidecars) {
            String path = uploadPath + "." + sidecar.extension();
            Path staged = stagingRoot.resolve(path);
            Files.createDirectories(staged.getParent());
            Files.writeString(staged, sidecar.value());
            out.add(new StagedArtifact(path, staged, Sha256.hex(staged)));
        }
    }

    private static String sidecarValue(List<PublishChecksum.Sidecar> sidecars, String extension) {
        for (PublishChecksum.Sidecar sidecar : sidecars) {
            if (sidecar.extension().equals(extension)) {
                return sidecar.value();
            }
        }
        throw new IllegalStateException("Missing " + extension + " checksum sidecar");
    }

    /**
     * The signing-configuration identity recorded in the resume manifest so a resume refuses a changed
     * setup: {@code unsigned}, or the pinned key id plus whether reproducible ({@code SOURCE_DATE_EPOCH})
     * signing is in effect — enough to detect a swapped key or a switch between wall-clock and
     * reproducible signing.
     */
    private String signingIdentity(PublishSigningSettings signing) {
        if (!signing.enabled()) {
            return "unsigned";
        }
        return "key=" + signing.keyId().orElse("") + ";sde=" + SourceDateEpoch.parse(environment).reproducible();
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
}
