package sh.zolt.toml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tomlj.Toml;
import sh.zolt.project.CoverageSettings;

final class CoverageSectionCodecTest {
    private final ZoltTomlParser parser = new ZoltTomlParser();

    @Test
    void parsesFloatAndIntegerFloors() {
        CoverageSettings settings = parseCoverage("""
                [coverage]
                minLine = 88.0
                minBranch = 74
                minInstruction = 90.3
                minMethod = 93
                """);
        assertEquals(Optional.of(88.0), settings.minLine());
        assertEquals(Optional.of(74.0), settings.minBranch());
        assertEquals(Optional.of(90.3), settings.minInstruction());
        assertEquals(Optional.of(93.0), settings.minMethod());
        assertTrue(settings.hasAnyFloor());
    }

    @Test
    void allowsPartialFloors() {
        CoverageSettings settings = parseCoverage("""
                [coverage]
                minLine = 80.0
                """);
        assertEquals(Optional.of(80.0), settings.minLine());
        assertTrue(settings.minBranch().isEmpty());
        assertTrue(settings.hasAnyFloor());
    }

    @Test
    void missingSectionYieldsNoFloors() {
        CoverageSettings settings = CoverageSectionCodec.parse(null);
        assertFalse(settings.hasAnyFloor());
        assertEquals(CoverageSettings.none(), settings);
    }

    @Test
    void topLevelCoverageSectionIsAccepted() {
        // A registered [coverage] section must not be rejected as unknown by the full parser.
        assertNotNull(parser.parse(project("""
                [coverage]
                minLine = 88.0
                minBranch = 74.0
                """)));
    }

    @Test
    void rejectsUnknownKey() {
        ZoltConfigException exception = assertThrows(
                ZoltConfigException.class,
                () -> parser.parse(project("""
                        [coverage]
                        minLines = 88.0
                        """)));
        assertTrue(exception.getMessage().contains("[coverage].minLines"));
    }

    @Test
    void rejectsOutOfRangeFloor() {
        ZoltConfigException exception = assertThrows(
                ZoltConfigException.class,
                () -> parser.parse(project("""
                        [coverage]
                        minLine = 150.0
                        """)));
        assertEquals(
                "Invalid value for [coverage].minLine in zolt.toml. Use a percentage between 0 and 100.",
                exception.getMessage());
    }

    @Test
    void rejectsNonNumericFloor() {
        ZoltConfigException exception = assertThrows(
                ZoltConfigException.class,
                () -> parser.parse(project("""
                        [coverage]
                        minLine = "high"
                        """)));
        assertTrue(exception.getMessage().contains("[coverage].minLine"));
    }

    @Test
    void parseCoverageFloorsReadsFile(@TempDir Path dir) throws Exception {
        Path config = dir.resolve("zolt.toml");
        Files.writeString(config, """
                [coverage]
                minLine = 88.0
                minBranch = 74.0
                """);
        CoverageSettings settings = parser.parseCoverageFloors(config);
        assertEquals(Optional.of(88.0), settings.minLine());
        assertEquals(Optional.of(74.0), settings.minBranch());
    }

    @Test
    void parseCoverageFloorsTreatsMissingFileAsNoFloors(@TempDir Path dir) {
        CoverageSettings settings = parser.parseCoverageFloors(dir.resolve("absent.toml"));
        assertFalse(settings.hasAnyFloor());
    }

    private static CoverageSettings parseCoverage(String coverageSection) {
        return CoverageSectionCodec.parse(Toml.parse(coverageSection).getTable("coverage"));
    }

    private static String project(String coverageSection) {
        return """
                [project]
                name = "demo"
                version = "0.1.0"
                group = "com.example"
                java = "21"

                """ + coverageSection;
    }
}
