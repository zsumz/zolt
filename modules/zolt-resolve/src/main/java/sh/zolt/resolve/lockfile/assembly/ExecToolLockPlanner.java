package sh.zolt.resolve.lockfile.assembly;

import sh.zolt.dependency.DependencyScope;
import sh.zolt.dependency.PackageId;
import sh.zolt.resolve.graph.PackageNode;
import sh.zolt.resolve.selection.SelectedDependencyScope;
import sh.zolt.resolve.selection.SelectedDependencyScopes;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds lock package plans for every jar in every isolated exec-tool closure (Hole 1). A jar shared by
 * two tools at the same version becomes one plan whose {@code toolGroups} unions both tool names; a jar
 * that appears at different versions across tools yields distinct plans (keyed by version), so a tool's
 * version line never collapses into another's. Tools arrive pre-sorted by name, so the first tool
 * alphabetically owns the retained node/graph for deterministic output.
 */
final class ExecToolLockPlanner {
    private ExecToolLockPlanner() {
    }

    static List<LockPackagePlan> plans(List<ExecToolResolution> execResolutions) {
        Map<String, LockPackagePlan> merged = new LinkedHashMap<>();
        for (ExecToolResolution tool : execResolutions) {
            Map<PackageId, List<SelectedDependencyScope>> toolScopes = SelectedDependencyScopes.from(
                    tool.graph(), tool.selection(), tool.directRequests());
            for (PackageNode node : tool.selection().selectedNodes()) {
                SelectedDependencyScope scope = toolScopes
                        .getOrDefault(node.packageId(), List.of(new SelectedDependencyScope(DependencyScope.TOOL_EXEC, false)))
                        .stream()
                        .findFirst()
                        .orElse(new SelectedDependencyScope(DependencyScope.TOOL_EXEC, false));
                LockPackagePlan plan = LockPackagePlan.of(node, scope, tool.graph(), List.of(tool.toolName()));
                merged.merge(node.packageId() + " " + node.selectedVersion(), plan, ExecToolLockPlanner::merge);
            }
        }
        return List.copyOf(merged.values());
    }

    private static LockPackagePlan merge(LockPackagePlan existing, LockPackagePlan incoming) {
        List<String> groups = new ArrayList<>(existing.toolGroups());
        for (String group : incoming.toolGroups()) {
            if (!groups.contains(group)) {
                groups.add(group);
            }
        }
        groups.sort(null);
        SelectedDependencyScope mergedScope = new SelectedDependencyScope(
                existing.selectedScope().scope(),
                existing.selectedScope().direct() || incoming.selectedScope().direct(),
                existing.selectedScope().artifactDescriptor().isPresent()
                        ? existing.selectedScope().artifactDescriptor()
                        : incoming.selectedScope().artifactDescriptor());
        return new LockPackagePlan(
                existing.node(), mergedScope, existing.artifactDescriptor(), existing.graph(), List.copyOf(groups));
    }
}
