package com.zolt.resolve;

import java.util.List;
import java.util.Optional;

record DependencyTraversalSelection(
        Optional<DependencyTraversalItem> selectedItem,
        List<DependencyPolicyEffect> policyEffects) {
    DependencyTraversalSelection {
        selectedItem = selectedItem == null ? Optional.empty() : selectedItem;
        policyEffects = List.copyOf(policyEffects);
    }

    static DependencyTraversalSelection selected(
            DependencyTraversalItem item,
            List<DependencyPolicyEffect> policyEffects) {
        return new DependencyTraversalSelection(Optional.of(item), policyEffects);
    }

    static DependencyTraversalSelection skipped() {
        return new DependencyTraversalSelection(Optional.empty(), List.of());
    }

    static DependencyTraversalSelection skippedWithEffects(List<DependencyPolicyEffect> policyEffects) {
        return new DependencyTraversalSelection(Optional.empty(), policyEffects);
    }
}
