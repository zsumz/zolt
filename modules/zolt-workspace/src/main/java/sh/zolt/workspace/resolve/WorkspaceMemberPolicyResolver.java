package sh.zolt.workspace.resolve;

import sh.zolt.project.ProjectConfig;
import sh.zolt.workspace.service.Workspace;
import sh.zolt.workspace.service.WorkspaceMember;

/**
 * Public entry point for the workspace-root policy merge that {@code zolt resolve --workspace} applies
 * to every member before resolving it. Produces the effective {@link ProjectConfig} a member resolves
 * against: workspace-root {@code [repositories]} and {@code [platforms]} folded in ahead of the
 * member's own declarations (member values win on identical keys; conflicting values are rejected).
 *
 * <p>This exposes the same merge {@link WorkspaceResolveService} performs internally, so callers that
 * resolve members outside the aggregating workspace flow — such as {@code zolt explain verify} — can
 * match {@code zolt resolve --workspace} semantics instead of resolving each member off its raw
 * {@code zolt.toml}.
 */
public final class WorkspaceMemberPolicyResolver {
    private final WorkspacePolicyMerger merger = new WorkspacePolicyMerger();

    /** Returns the effective member config with workspace-root repositories and platforms merged in. */
    public ProjectConfig merge(Workspace workspace, WorkspaceMember member) {
        return merger.merge(workspace, member);
    }
}
