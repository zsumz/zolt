package sh.zolt.update;

import sh.zolt.dependency.UpdateClass;
import java.util.List;

/**
 * A single planned version change: which surface, its section and identifier, the from/to versions,
 * the change class, and — for a version alias — the full fan-out of coordinates the change reaches.
 */
public record UpdateEdit(
        OutdatedSurface surface,
        String identifier,
        String section,
        String fromVersion,
        String toVersion,
        UpdateClass changeClass,
        List<String> fanOut) {
    public UpdateEdit {
        fanOut = fanOut == null ? List.of() : List.copyOf(fanOut);
    }
}
