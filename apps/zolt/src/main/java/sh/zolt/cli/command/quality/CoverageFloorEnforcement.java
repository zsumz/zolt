package sh.zolt.cli.command.quality;

import sh.zolt.cli.command.CommandFailures;
import sh.zolt.error.ActionableError;
import sh.zolt.project.CoverageSettings;
import sh.zolt.quality.coverage.CoverageFloorEvaluation;
import sh.zolt.quality.coverage.CoverageMeasurement;
import sh.zolt.quality.coverage.CoverageReportException;
import sh.zolt.quality.coverage.JacocoCoverageReport;
import java.nio.file.Path;
import java.util.Optional;
import picocli.CommandLine.Model.CommandSpec;

/**
 * Enforces configured {@code [coverage]} floors after a coverage report is generated. When no floors
 * are configured this is a no-op, so coverage behavior is unchanged for projects that opt out.
 */
final class CoverageFloorEnforcement {
    private CoverageFloorEnforcement() {
    }

    static void enforce(CommandSpec spec, CoverageSettings floors, Optional<Path> xmlReport) {
        if (!floors.hasAnyFloor()) {
            return;
        }
        if (xmlReport.isEmpty()) {
            throw CommandFailures.user(spec, ActionableError.of(
                    "Coverage floors are configured in zolt.toml, but the Jacoco XML report is disabled.",
                    "Remove --no-xml so coverage floors can be evaluated, or remove the [coverage] floors."));
        }
        Path report = xmlReport.get();
        CoverageMeasurement measurement;
        try {
            measurement = JacocoCoverageReport.read(report);
        } catch (CoverageReportException exception) {
            throw CommandFailures.user(spec, ActionableError.of(
                    "Could not read the Jacoco coverage report at " + report + ".",
                    "Re-run `zolt coverage` to regenerate the report."));
        }
        CoverageFloorEvaluation evaluation = CoverageFloorEvaluation.evaluate(floors, measurement);
        if (!evaluation.passed()) {
            throw CommandFailures.user(spec, ActionableError.of(
                    evaluation.summary(),
                    "Raise coverage to the configured floors or adjust the [coverage] floors in zolt.toml."));
        }
    }
}
