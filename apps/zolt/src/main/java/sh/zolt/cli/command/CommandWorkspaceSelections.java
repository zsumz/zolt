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
}
