package sh.zolt.cli.command;

import sh.zolt.workspace.service.WorkspaceSelectionRequest;
import java.util.ArrayList;
import java.util.List;

public final class CommandWorkspaceSelections {
    private CommandWorkspaceSelections() {
    }

    public static WorkspaceSelectionRequest from(boolean all, List<String> members, List<String> memberGroups) {
        List<String> selectedMembers = new ArrayList<>();
        selectedMembers.addAll(members);
        selectedMembers.addAll(memberGroups);
        return new WorkspaceSelectionRequest(all, selectedMembers);
    }

    /**
     * As {@link #from(boolean, List, List)}, but when {@code resumeMembers} is non-empty it takes
     * precedence and yields an EXACT, non-expanding selection — the plain-repository publish resume
     * seam, where a dependency-expanded re-selection would re-include already-uploaded providers.
     */
    public static WorkspaceSelectionRequest from(
            boolean all, List<String> members, List<String> memberGroups, List<String> resumeMembers) {
        if (!resumeMembers.isEmpty()) {
            return WorkspaceSelectionRequest.exact(resumeMembers);
        }
        return from(all, members, memberGroups);
    }
}
