package com.zolt.cli.command;

import com.zolt.workspace.WorkspaceSelectionRequest;
import java.util.ArrayList;
import java.util.List;

final class CommandWorkspaceSelections {
    private CommandWorkspaceSelections() {
    }

    static WorkspaceSelectionRequest from(boolean all, List<String> members, List<String> memberGroups) {
        List<String> selectedMembers = new ArrayList<>();
        selectedMembers.addAll(members);
        selectedMembers.addAll(memberGroups);
        return new WorkspaceSelectionRequest(all, selectedMembers);
    }
}
