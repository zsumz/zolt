package sh.zolt.workspace.service;

import java.util.List;

/**
 * A workspace member selection request. {@code exact} selects the {@code members} verbatim without
 * dependency expansion — used by a plain-repository publish resume, where already-uploaded providers
 * must not be re-included (an immutable release repository rejects re-PUT of what already landed).
 * The default (non-exact) selection expands a member to include its workspace dependencies.
 */
public record WorkspaceSelectionRequest(
        boolean all,
        List<String> members,
        boolean exact) {
    public WorkspaceSelectionRequest {
        members = members == null ? List.of() : List.copyOf(members);
    }

    public WorkspaceSelectionRequest(boolean all, List<String> members) {
        this(all, members, false);
    }

    public static WorkspaceSelectionRequest defaults() {
        return new WorkspaceSelectionRequest(false, List.of(), false);
    }

    /** An exact, non-expanding selection of {@code members} — the plain-repository publish resume seam. */
    public static WorkspaceSelectionRequest exact(List<String> members) {
        return new WorkspaceSelectionRequest(false, members, true);
    }
}
