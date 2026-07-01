package com.zolt.project;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record DependencyPolicySettings(
        List<DependencyPolicyExclusion> exclusions,
        Map<String, DependencyConstraint> constraints,
        boolean failOnVersionConflict) {
    public DependencyPolicySettings(
            List<DependencyPolicyExclusion> exclusions,
            Map<String, DependencyConstraint> constraints) {
        this(exclusions, constraints, false);
    }

    public DependencyPolicySettings {
        exclusions = exclusions == null ? List.of() : List.copyOf(exclusions);
        constraints = constraints == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(constraints));
    }

    public static DependencyPolicySettings defaults() {
        return new DependencyPolicySettings(List.of(), Map.of(), false);
    }
}
