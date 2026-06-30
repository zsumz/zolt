package com.zolt.workspace.service;

import java.util.List;

public record WorkspaceSelection(
        List<String> includedMembers,
        List<String> selectedMembers) {
    public WorkspaceSelection {
        includedMembers = List.copyOf(includedMembers);
        selectedMembers = List.copyOf(selectedMembers);
    }
}
