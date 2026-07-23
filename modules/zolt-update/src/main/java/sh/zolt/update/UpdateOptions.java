package sh.zolt.update;

import java.util.List;

/**
 * Engine inputs to an update plan: the change ceiling, whether prereleases widen candidates, whether
 * discovery is offline, and selectors scoping which surfaces update. Whether to write and whether to
 * re-resolve are command-level concerns applied after planning.
 */
public record UpdateOptions(
        UpdateCeiling ceiling,
        boolean includePrereleases,
        boolean offline,
        List<String> selectors) {
    public UpdateOptions {
        ceiling = ceiling == null ? UpdateCeiling.DEFAULT : ceiling;
        selectors = selectors == null ? List.of() : List.copyOf(selectors);
    }
}
