package sh.zolt.explain.maven;

import java.util.List;
import java.util.Map;

/**
 * The outcome of attempting to recover an external Maven parent chain for one reactor module.
 *
 * <ul>
 *   <li>{@link #none()} — recovery was not attempted (offline audit, or the module has no external
 *       parent). The offline behaviour is preserved byte-for-byte.
 *   <li>{@link #unresolved(String)} — recovery was attempted but could not run (SNAPSHOT or dynamic
 *       parent version, unreachable repository). The parent stays unresolved and {@link #reviewNote()}
 *       carries the honest remediation used as the {@code maven.parent.unresolved} next step.
 *   <li>{@link #resolved(Map, List, List)} — the external chain was fetched. {@link #properties()} and
 *       {@link #managedDependencies()} merge into the inspection so inherited versions resolve, and
 *       {@link #fetchedArtifacts()} records the coordinates and source repositories for audit honesty.
 * </ul>
 */
public record RecoveredParentMetadata(
        boolean resolved,
        Map<String, String> properties,
        List<RecoveredManagedDependency> managedDependencies,
        List<RecoveredArtifact> fetchedArtifacts,
        String reviewNote) {
    public RecoveredParentMetadata {
        properties = properties == null ? Map.of() : Map.copyOf(properties);
        managedDependencies = managedDependencies == null ? List.of() : List.copyOf(managedDependencies);
        fetchedArtifacts = fetchedArtifacts == null ? List.of() : List.copyOf(fetchedArtifacts);
        reviewNote = reviewNote == null ? "" : reviewNote;
    }

    /** Recovery was not attempted; the offline audit stands unchanged. */
    public static RecoveredParentMetadata none() {
        return new RecoveredParentMetadata(false, Map.of(), List.of(), List.of(), "");
    }

    /** Recovery was attempted but the external parent could not be fetched or used. */
    public static RecoveredParentMetadata unresolved(String reviewNote) {
        return new RecoveredParentMetadata(false, Map.of(), List.of(), List.of(), reviewNote);
    }

    /** The external parent chain was fetched and its metadata recovered. */
    public static RecoveredParentMetadata resolved(
            Map<String, String> properties,
            List<RecoveredManagedDependency> managedDependencies,
            List<RecoveredArtifact> fetchedArtifacts) {
        return new RecoveredParentMetadata(true, properties, managedDependencies, fetchedArtifacts, "");
    }

    /** Whether recovery was attempted (either resolved, or attempted-and-failed with a review note). */
    public boolean attempted() {
        return resolved || !reviewNote.isBlank();
    }
}
