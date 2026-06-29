package com.zolt.workspace;

import com.zolt.build.CleanResult;
import java.util.List;

public record WorkspaceCleanResult(
        WorkspaceSelection selection,
        List<MemberCleanResult> members) {
    public WorkspaceCleanResult {
        members = List.copyOf(members);
    }

    public int deletedCount() {
        return members.stream()
                .map(MemberCleanResult::result)
                .mapToInt(CleanResult::deletedCount)
                .sum();
    }

    public record MemberCleanResult(
            String member,
            CleanResult result) {
    }
}
