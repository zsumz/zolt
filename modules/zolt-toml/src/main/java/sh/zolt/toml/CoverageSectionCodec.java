package sh.zolt.toml;

import sh.zolt.project.CoverageSettings;
import sh.zolt.toml.support.TomlScalars;
import sh.zolt.toml.support.TomlValidation;
import java.util.Set;
import org.tomlj.TomlTable;

/**
 * Parses the optional {@code [coverage]} section into {@link CoverageSettings}. Coverage floors are
 * project-owned quality policy: the minimum Jacoco percentages a project accepts. Supported keys are
 * {@code minLine}, {@code minBranch}, {@code minInstruction}, and {@code minMethod}, each a
 * percentage in the inclusive {@code 0..100} range.
 */
public final class CoverageSectionCodec {
    private static final Set<String> COVERAGE_KEYS =
            Set.of("minLine", "minBranch", "minInstruction", "minMethod");

    private CoverageSectionCodec() {
    }

    public static CoverageSettings parse(TomlTable coverageTable) {
        if (coverageTable == null) {
            return CoverageSettings.none();
        }
        TomlValidation.validateKeys("coverage", coverageTable, COVERAGE_KEYS);
        try {
            return new CoverageSettings(
                    TomlScalars.optionalDouble(coverageTable, "coverage", "minLine"),
                    TomlScalars.optionalDouble(coverageTable, "coverage", "minBranch"),
                    TomlScalars.optionalDouble(coverageTable, "coverage", "minInstruction"),
                    TomlScalars.optionalDouble(coverageTable, "coverage", "minMethod"));
        } catch (IllegalArgumentException exception) {
            throw new ZoltConfigException(exception.getMessage());
        }
    }
}
