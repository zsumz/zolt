package sh.zolt.resolve.version;

import sh.zolt.resolve.graph.PackageNode;
import java.util.List;

public record VersionSelectionResult(
        List<PackageNode> selectedNodes,
        List<VersionConflict> conflicts) {
    public VersionSelectionResult {
        selectedNodes = List.copyOf(selectedNodes);
        conflicts = List.copyOf(conflicts);
    }
}
