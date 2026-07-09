package sh.zolt.workspace.service;

import sh.zolt.doctor.JdkChecker;
import java.util.Objects;

@FunctionalInterface
public interface WorkspaceJdkCheckerResolver {
    JdkChecker forMember(Workspace workspace, WorkspaceMember member);

    static WorkspaceJdkCheckerResolver fixed(JdkChecker jdkChecker) {
        Objects.requireNonNull(jdkChecker, "jdkChecker");
        return (workspace, member) -> jdkChecker;
    }
}
