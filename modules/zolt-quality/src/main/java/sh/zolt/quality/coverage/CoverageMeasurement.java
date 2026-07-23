package sh.zolt.quality.coverage;

import java.util.Map;
import java.util.OptionalDouble;

/**
 * Report-level Jacoco coverage totals, one {@link MetricCount} per {@link CoverageMetric} present in
 * the report. Percentages are computed as {@code covered / (covered + missed) * 100}; a metric with
 * no observations (zero total) is reported as {@link OptionalDouble#empty()} since no floor can be
 * meaningfully applied.
 */
public record CoverageMeasurement(Map<CoverageMetric, MetricCount> counts) {
    public CoverageMeasurement {
        counts = counts == null ? Map.of() : Map.copyOf(counts);
    }

    public OptionalDouble percentage(CoverageMetric metric) {
        MetricCount count = counts.get(metric);
        if (count == null || count.total() == 0) {
            return OptionalDouble.empty();
        }
        return OptionalDouble.of(count.covered() * 100.0 / count.total());
    }

    public record MetricCount(long covered, long missed) {
        public MetricCount {
            if (covered < 0 || missed < 0) {
                throw new IllegalArgumentException("Coverage counts must be non-negative.");
            }
        }

        public long total() {
            return covered + missed;
        }
    }
}
