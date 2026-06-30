package com.zolt.workspace.discovery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.zolt.workspace.WorkspaceConfigException;
import com.zolt.workspace.WorkspaceMember;
import com.zolt.workspace.WorkspaceProjectEdge;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

final class WorkspaceBuildOrderPlannerTest {
    private final WorkspaceBuildOrderPlanner planner = new WorkspaceBuildOrderPlanner();

    @Test
    void ordersDependenciesBeforeDependentsWithoutReorderingUnrelatedMembers() {
        List<String> buildOrder = planner.buildOrder(
                List.of(member("apps/api"), member("modules/core"), member("apps/worker")),
                List.of(new WorkspaceProjectEdge("apps/api", "modules/core", "compile", "com.acme:core")));

        assertEquals(List.of("modules/core", "apps/api", "apps/worker"), buildOrder);
    }

    @Test
    void reportsDeterministicCyclePath() {
        WorkspaceConfigException exception = assertThrows(
                WorkspaceConfigException.class,
                () -> planner.buildOrder(
                        List.of(member("apps/api"), member("modules/core"), member("modules/util")),
                        List.of(
                                new WorkspaceProjectEdge("apps/api", "modules/core", "compile", "com.acme:core"),
                                new WorkspaceProjectEdge("modules/core", "modules/util", "compile", "com.acme:util"),
                                new WorkspaceProjectEdge("modules/util", "apps/api", "compile", "com.acme:api"))));

        assertEquals(
                "Workspace dependency cycle detected: apps/api -> modules/core -> modules/util -> apps/api.",
                exception.getMessage());
    }

    private static WorkspaceMember member(String path) {
        return new WorkspaceMember(path, Path.of(path), null);
    }
}
