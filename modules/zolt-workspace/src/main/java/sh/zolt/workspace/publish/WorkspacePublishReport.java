package sh.zolt.workspace.publish;

import sh.zolt.publish.PublishDryRunPlan;
import java.util.List;
import java.util.Optional;

/**
 * The outcome of {@code zolt publish --workspace}: the per-member family plan (Phase 1) and, when
 * uploaded, the Phase-2 result. Any non-empty {@link #blockers()} means nothing was uploaded — the
 * offline family plan aggregates every member's blockers into one report so an operator fixes them
 * all before a single artifact leaves the machine.
 */
public record WorkspacePublishReport(
        List<Member> members,
        List<String> blockers,
        boolean uploaded,
        Optional<String> deploymentId,
        Optional<String> resumeCommand) {
    public WorkspacePublishReport {
        members = List.copyOf(members);
        blockers = List.copyOf(blockers);
        deploymentId = deploymentId == null ? Optional.empty() : deploymentId;
        resumeCommand = resumeCommand == null ? Optional.empty() : resumeCommand;
    }

    public boolean ok() {
        return blockers.isEmpty();
    }

    /** One publishable member's Phase-1 plan (a BOM's plan is pom-only). */
    public record Member(String memberPath, String coordinate, boolean bom, PublishDryRunPlan plan) {
    }
}
