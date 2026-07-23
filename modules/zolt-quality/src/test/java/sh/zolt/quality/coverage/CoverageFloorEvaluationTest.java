package sh.zolt.quality.coverage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import sh.zolt.project.CoverageSettings;

final class CoverageFloorEvaluationTest {

    @Test
    void passesWhenAboveFloor() {
        CoverageFloorEvaluation evaluation = CoverageFloorEvaluation.evaluate(
                floors(88.0, 74.0),
                measurement(Map.of(
                        CoverageMetric.LINE, count(90, 10),
                        CoverageMetric.BRANCH, count(80, 20))));
        assertTrue(evaluation.passed());
    }

    @Test
    void passesWhenExactlyAtFloor() {
        CoverageFloorEvaluation evaluation = CoverageFloorEvaluation.evaluate(
                floors(88.0, 74.0),
                measurement(Map.of(
                        CoverageMetric.LINE, count(88, 12),
                        CoverageMetric.BRANCH, count(74, 26))));
        assertTrue(evaluation.passed());
    }

    @Test
    void failsWhenBelowFloorNamingEachMetric() {
        CoverageFloorEvaluation evaluation = CoverageFloorEvaluation.evaluate(
                floors(88.0, 74.0),
                measurement(Map.of(
                        CoverageMetric.LINE, count(86, 14),
                        CoverageMetric.BRANCH, count(70, 30))));
        assertFalse(evaluation.passed());
        assertEquals(2, evaluation.violations().size());
        assertEquals(CoverageMetric.LINE, evaluation.violations().get(0).metric());
        assertEquals(CoverageMetric.BRANCH, evaluation.violations().get(1).metric());
        assertEquals(
                "line coverage 86.0% is below the configured floor 88.0%",
                evaluation.violations().get(0).describe());
        assertTrue(evaluation.summary().contains("line coverage 86.0% is below the configured floor 88.0%"));
        assertTrue(evaluation.summary().contains("branch coverage 70.0% is below the configured floor 74.0%"));
    }

    @Test
    void skipsUnconfiguredMetrics() {
        CoverageFloorEvaluation evaluation = CoverageFloorEvaluation.evaluate(
                floors(88.0, null),
                measurement(Map.of(
                        CoverageMetric.LINE, count(90, 10),
                        CoverageMetric.BRANCH, count(1, 99))));
        assertTrue(evaluation.passed());
    }

    @Test
    void skipsNotApplicableMetric() {
        CoverageFloorEvaluation evaluation = CoverageFloorEvaluation.evaluate(
                floors(null, 74.0),
                measurement(Map.of(CoverageMetric.BRANCH, count(0, 0))));
        assertTrue(evaluation.passed());
    }

    @Test
    void noFloorsAlwaysPasses() {
        CoverageFloorEvaluation evaluation = CoverageFloorEvaluation.evaluate(
                CoverageSettings.none(),
                measurement(Map.of(CoverageMetric.LINE, count(1, 99))));
        assertTrue(evaluation.passed());
    }

    private static CoverageSettings floors(Double minLine, Double minBranch) {
        return new CoverageSettings(
                Optional.ofNullable(minLine),
                Optional.ofNullable(minBranch),
                Optional.empty(),
                Optional.empty());
    }

    private static CoverageMeasurement.MetricCount count(long covered, long missed) {
        return new CoverageMeasurement.MetricCount(covered, missed);
    }

    private static CoverageMeasurement measurement(Map<CoverageMetric, CoverageMeasurement.MetricCount> counts) {
        return new CoverageMeasurement(new EnumMap<>(counts));
    }
}
