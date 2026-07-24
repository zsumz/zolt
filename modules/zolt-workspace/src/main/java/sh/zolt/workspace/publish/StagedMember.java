package sh.zolt.workspace.publish;

import java.util.List;

/**
 * One member's fully-staged plain-repository publication: its resolved {@link RepositoryTarget} and
 * the ordered {@link StagedArtifact} set Phase 2 uploads (primary artifact, each with its checksums
 * and — when signing is enabled — its detached signature and that signature's checksums, then every
 * supplemental, then the POM). {@link #reportMember()} carries the public display projection so the
 * uploader can render the family report and name this member in a resume command without re-reading
 * the plan.
 */
record StagedMember(
        WorkspacePublishReport.Member reportMember, RepositoryTarget target, List<StagedArtifact> artifacts) {

    String memberPath() {
        return reportMember.memberPath();
    }

    String coordinate() {
        return reportMember.coordinate();
    }
}
