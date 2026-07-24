package sh.zolt.workspace.publish;

import sh.zolt.publish.PublishCentralPublishOutcome;
import sh.zolt.publish.PublishDryRunPlan;
import java.util.List;
import java.util.Optional;

/**
 * The outcome of {@code zolt publish --workspace}: the per-member family plan (Phase 1) and, when
 * uploaded, the Phase-2 result. Any non-empty {@link #blockers()} means nothing was uploaded — the
 * offline family plan aggregates every member's blockers into one report so an operator fixes them
 * all before a single artifact leaves the machine.
 *
 * <p>{@link #notes()} carry non-blocking Phase-1 observations (for example, a resumed publish whose
 * already-published provider is legitimately absent). {@link #centralOutcome()} is present only for a
 * live {@code --central} family publish and mirrors the single-project terminal status (uploaded,
 * published, or validated-awaiting-manual-release).
 */
public record WorkspacePublishReport(
        List<Member> members,
        List<String> blockers,
        List<String> notes,
        boolean uploaded,
        Optional<String> deploymentId,
        Optional<String> resumeCommand,
        Optional<PublishCentralPublishOutcome> centralOutcome) {
    public WorkspacePublishReport {
        members = List.copyOf(members);
        blockers = List.copyOf(blockers);
        notes = notes == null ? List.of() : List.copyOf(notes);
        deploymentId = deploymentId == null ? Optional.empty() : deploymentId;
        resumeCommand = resumeCommand == null ? Optional.empty() : resumeCommand;
        centralOutcome = centralOutcome == null ? Optional.empty() : centralOutcome;
    }

    /** Historical shape: no Phase-1 notes and no Central terminal outcome. */
    public WorkspacePublishReport(
            List<Member> members,
            List<String> blockers,
            boolean uploaded,
            Optional<String> deploymentId,
            Optional<String> resumeCommand) {
        this(members, blockers, List.of(), uploaded, deploymentId, resumeCommand, Optional.empty());
    }

    public boolean ok() {
        return blockers.isEmpty();
    }

    /** Returns a copy carrying {@code notes}; the same report when there are none to add. */
    public WorkspacePublishReport withNotes(List<String> notes) {
        if (notes == null || notes.isEmpty()) {
            return this;
        }
        return new WorkspacePublishReport(
                members, blockers, notes, uploaded, deploymentId, resumeCommand, centralOutcome);
    }

    /** One publishable member's Phase-1 plan (a BOM's plan is pom-only). */
    public record Member(String memberPath, String coordinate, boolean bom, PublishDryRunPlan plan) {
    }
}
