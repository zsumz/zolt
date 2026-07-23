package sh.zolt.update;

import java.util.List;

/**
 * Inputs that shape an outdated report: whether prereleases widen candidates, whether up-to-date
 * surfaces are included in the output, whether discovery is offline (cache-only), and optional
 * selectors scoping the report to specific coordinates, aliases, or section tokens.
 */
public record OutdatedOptions(
        boolean includePrereleases,
        boolean includeUpToDate,
        boolean offline,
        List<String> selectors) {
    public OutdatedOptions {
        selectors = selectors == null ? List.of() : List.copyOf(selectors);
    }

    public static OutdatedOptions defaults() {
        return new OutdatedOptions(false, false, false, List.of());
    }
}
