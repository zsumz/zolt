package com.zolt.plan;

import java.nio.file.Path;
import java.util.List;

public record BuildPlan(
        int schemaVersion,
        Path projectRoot,
        String projectName,
        PlanTarget target,
        List<PlanNode> nodes) {
    public BuildPlan {
        schemaVersion = schemaVersion <= 0 ? 1 : schemaVersion;
        projectRoot = projectRoot.toAbsolutePath().normalize();
        projectName = projectName == null ? "" : projectName;
        nodes = nodes == null ? List.of() : List.copyOf(nodes);
    }

    public boolean blocked() {
        return nodes.stream().anyMatch(node -> node.status() == PlanNodeStatus.BLOCKED);
    }
}
