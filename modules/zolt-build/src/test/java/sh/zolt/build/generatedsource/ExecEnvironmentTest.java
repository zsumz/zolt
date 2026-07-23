package sh.zolt.build.generatedsource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import sh.zolt.project.ExecGenerationSettings;
import sh.zolt.project.ExecToolSettings;
import sh.zolt.project.GeneratedSourceKind;
import sh.zolt.project.GeneratedSourceStep;
import sh.zolt.project.OpenApiGenerationSettings;
import sh.zolt.project.ProducesLane;
import sh.zolt.project.ProtobufGenerationSettings;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;

/**
 * OS-name is injected so the Windows-only environment curation is verifiable on a POSIX host and is a
 * strict no-op off Windows (existing macOS/Linux exec fingerprints are unchanged).
 */
final class ExecEnvironmentTest {
    private static final Path ROOT = Path.of("/project");
    private static final Path OUTPUT = Path.of("/project/target/gen");

    @Test
    void windowsCarriesSystemRootAndSystemDriveIntoTheClearedEnvironment() {
        Map<String, String> environment = ExecEnvironment.build(
                ROOT, OUTPUT, "sources", step(), ambient(), "Windows 11");

        assertEquals("C:\\Windows", environment.get("SystemRoot"));
        assertEquals("C:", environment.get("SystemDrive"));
        assertEquals("C:\\bin", environment.get("PATH"));
    }

    @Test
    void nonWindowsOmitsWindowsSpecificEssentials() {
        Map<String, String> environment = ExecEnvironment.build(
                ROOT, OUTPUT, "sources", step(), ambient(), "Linux");

        assertFalse(environment.containsKey("SystemRoot"));
        assertFalse(environment.containsKey("SystemDrive"));
        assertEquals("C:\\bin", environment.get("PATH"));
    }

    private static GeneratedSourceStep step() {
        return new GeneratedSourceStep(
                "gen",
                GeneratedSourceKind.EXEC,
                "java",
                "target/gen",
                List.of(),
                true,
                true,
                OpenApiGenerationSettings.empty(),
                ProtobufGenerationSettings.empty(),
                new ExecGenerationSettings(
                        "gen-tool",
                        ExecToolSettings.empty(),
                        List.of(),
                        ProducesLane.JAVA_SOURCES,
                        Optional.empty(),
                        Map.of(),
                        "content"));
    }

    private static UnaryOperator<String> ambient() {
        Map<String, String> values = new HashMap<>();
        values.put("PATH", "C:\\bin");
        values.put("SystemRoot", "C:\\Windows");
        values.put("SystemDrive", "C:");
        return values::get;
    }
}
