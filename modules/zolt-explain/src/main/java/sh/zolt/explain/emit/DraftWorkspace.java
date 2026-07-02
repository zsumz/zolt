package sh.zolt.explain.emit;

import sh.zolt.workspace.WorkspaceConfig;
import java.util.List;

/**
 * A draft Zolt workspace synthesized from a multi-module Maven reactor or Gradle multi-project build.
 *
 * <p>{@link #workspace()} is the root {@code [workspace]} document (name, members, defaultMembers).
 * {@link #members()} are the per-module drafts, each tagged with the relative directory it belongs
 * in, so the renderer can label every document with its target {@code <path>/zolt.toml}.
 * {@link #notes()} are workspace-level review items (e.g. deps declared on the root aggregator that
 * a {@code [workspace]} cannot carry).
 */
public record DraftWorkspace(WorkspaceConfig workspace, List<Member> members, List<String> notes)
        implements DraftEmit {
    public DraftWorkspace {
        members = List.copyOf(members);
        notes = List.copyOf(notes);
    }

    /** One member of a draft workspace: its relative path plus the draft zolt.toml for that module. */
    public record Member(String path, DraftZoltToml draft) {
        public Member {
            if (path == null || path.isBlank()) {
                throw new IllegalArgumentException("A workspace member must have a non-blank relative path.");
            }
        }
    }
}
