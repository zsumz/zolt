package sh.zolt.workspace.service;

import sh.zolt.build.testruntime.TestRunService;
import java.util.Objects;

@FunctionalInterface
public interface WorkspaceTestRunServiceResolver {
    TestRunService forMember(Workspace workspace, WorkspaceMember member);

    static WorkspaceTestRunServiceResolver fixed(TestRunService testRunService) {
        Objects.requireNonNull(testRunService, "testRunService");
        return (workspace, member) -> testRunService;
    }
}
