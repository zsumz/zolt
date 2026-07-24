package sh.zolt.workspace.publish;

import sh.zolt.maven.repository.RepositoryAuthentication;
import sh.zolt.project.RepositoryUrlPolicy;
import sh.zolt.publish.PublishArtifactPlan;
import sh.zolt.publish.PublishDryRunPlan;
import sh.zolt.publish.PublishException;
import sh.zolt.publish.PublicationResume;
import sh.zolt.publish.PublicationSource;
import sh.zolt.publish.PublicationStagingService;
import sh.zolt.publish.PublishRepositoryAuthentication;
import sh.zolt.publish.PublishRepositorySettings;
import sh.zolt.publish.PublishSigningSettings;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
    private final PublicationStagingService publicationStaging;

    WorkspacePublishStaging() {
        this(System::getenv);
    }

    WorkspacePublishStaging(Function<String, String> environment) {
        this.environment = environment;
        this.publicationStaging = new PublicationStagingService(environment);
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
                publicationStaging.preflight(signing);
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
            } catch (PublishException exception) {
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

    private StagedMember stageMember(Resolved entry, Path stagingRoot, Optional<ResumeState> resume) {
        MemberPublication member = entry.member();
        PublishDryRunPlan plan = member.plan();
        PublishSigningSettings signing = member.publish().signing();
        Path memberRoot = member.memberRoot();
        List<PublicationSource> sources = new ArrayList<>();
        if (!plan.pomOnly()) {
            sources.add(new PublicationSource(
                    plan.artifactUploadPath(), memberRoot.resolve(plan.artifactPath()).normalize()));
        }
        for (PublishArtifactPlan supplemental : plan.supplementalArtifacts()) {
            sources.add(new PublicationSource(
                    supplemental.uploadPath(), memberRoot.resolve(supplemental.path()).normalize()));
        }
        sources.add(new PublicationSource(
                plan.pomUploadPath(), memberRoot.resolve(plan.pomPath()).normalize()));
        PublicationResume publicationResume =
                new PublicationResume(resume.map(ResumeState::stagedHashes).orElse(Map.of()));
        List<StagedArtifact> artifacts = publicationStaging
                .stage(stagingRoot, sources, signing, publicationResume)
                .stream()
                .map(file -> new StagedArtifact(file.repositoryPath(), file.source(), file.sha256()))
                .toList();
        return new StagedMember(
                member.toReportMember(),
                entry.target(),
                publicationStaging.signingIdentity(signing),
                artifacts);
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
