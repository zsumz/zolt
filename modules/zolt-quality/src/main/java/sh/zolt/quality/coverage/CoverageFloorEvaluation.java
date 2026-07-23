package sh.zolt.quality.coverage;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import sh.zolt.project.CoverageSettings;

/**
 * The outcome of comparing a {@link CoverageMeasurement} against the configured {@link CoverageSettings}
 * floors. A metric is a violation only when it is gated, measurable (non-zero total), and strictly
 * below its floor; a measurement exactly at the floor passes. Violations are ordered by
 * {@link CoverageMetric} declaration order for deterministic output.
 */
public record CoverageFloorEvaluation(List<CoverageFloorViolation> violations) {
    public CoverageFloorEvaluation {
        violations = List.copyOf(violations);
    }

    public boolean passed() {
        return violations.isEmpty();
    }

    /** A single sentence naming each failed metric, for an actionable error message. */
    public String summary() {
        List<String> parts = new ArrayList<>();
        for (CoverageFloorViolation violation : violations) {
            parts.add(violation.describe());
        }
        return "Coverage floors not met: " + String.join("; ", parts) + ".";
    }

    public static CoverageFloorEvaluation evaluate(CoverageSettings floors, CoverageMeasurement measurement) {
        List<CoverageFloorViolation> violations = new ArrayList<>();
        for (CoverageMetric metric : CoverageMetric.values()) {
            Optional<Double> configured = metric.floor(floors);
            if (configured.isEmpty()) {
                continue;
            }
            OptionalDouble actual = measurement.percentage(metric);
            if (actual.isEmpty()) {
                // No code of this kind (e.g. a project with zero branches): the floor cannot apply.
                continue;
            }
            if (actual.getAsDouble() < configured.get()) {
                violations.add(new CoverageFloorViolation(metric, actual.getAsDouble(), configured.get()));
            }
        }
        return new CoverageFloorEvaluation(violations);
    }
}
