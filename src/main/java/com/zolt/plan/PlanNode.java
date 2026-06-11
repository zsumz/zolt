package com.zolt.plan;

import java.util.List;

public record PlanNode(
        String id,
        String kind,
        PlanNodeStatus status,
        String description,
        List<String> inputs,
        List<String> outputs,
        List<String> details,
        List<PlanBlocker> blockers) {
    public PlanNode {
        id = requireNonBlank(id, "Plan node id");
        kind = requireNonBlank(kind, "Plan node kind");
        status = status == null ? PlanNodeStatus.READY : status;
        description = requireNonBlank(description, "Plan node description");
        inputs = inputs == null ? List.of() : List.copyOf(inputs);
        outputs = outputs == null ? List.of() : List.copyOf(outputs);
        details = details == null ? List.of() : List.copyOf(details);
        blockers = blockers == null ? List.of() : List.copyOf(blockers);
    }

    private static String requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must be a non-empty string.");
        }
        return value;
    }
}
