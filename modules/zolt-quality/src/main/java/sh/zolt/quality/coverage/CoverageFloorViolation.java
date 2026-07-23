package sh.zolt.quality.coverage;

import java.util.Locale;

/** A single metric whose measured coverage fell below its configured floor. */
public record CoverageFloorViolation(CoverageMetric metric, double actualPercent, double floorPercent) {
    /** For example: {@code "line coverage 86.1% is below the configured floor 88.0%"}. */
    public String describe() {
        return metric.displayName() + " coverage " + formatPercent(actualPercent)
                + " is below the configured floor " + formatPercent(floorPercent);
    }

    static String formatPercent(double value) {
        return String.format(Locale.ROOT, "%.1f%%", value);
    }
}
