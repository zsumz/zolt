package sh.zolt.quality.coverage;

import java.util.Optional;
import sh.zolt.project.CoverageSettings;

/**
 * A Jacoco coverage counter type that can be gated by a floor. The {@link #jacocoType()} matches the
 * {@code type} attribute of a report-level {@code <counter>} element in {@code jacoco.xml}.
 */
public enum CoverageMetric {
    LINE("LINE", "line"),
    BRANCH("BRANCH", "branch"),
    INSTRUCTION("INSTRUCTION", "instruction"),
    METHOD("METHOD", "method");

    private final String jacocoType;
    private final String displayName;

    CoverageMetric(String jacocoType, String displayName) {
        this.jacocoType = jacocoType;
        this.displayName = displayName;
    }

    public String jacocoType() {
        return jacocoType;
    }

    public String displayName() {
        return displayName;
    }

    /** The configured floor for this metric, or empty when the metric is not gated. */
    public Optional<Double> floor(CoverageSettings settings) {
        return switch (this) {
            case LINE -> settings.minLine();
            case BRANCH -> settings.minBranch();
            case INSTRUCTION -> settings.minInstruction();
            case METHOD -> settings.minMethod();
        };
    }

    static Optional<CoverageMetric> fromJacocoType(String type) {
        for (CoverageMetric metric : values()) {
            if (metric.jacocoType.equals(type)) {
                return Optional.of(metric);
            }
        }
        return Optional.empty();
    }
}
