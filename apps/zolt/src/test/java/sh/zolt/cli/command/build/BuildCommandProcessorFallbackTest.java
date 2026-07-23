package sh.zolt.cli.command.build;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import sh.zolt.build.BuildResult;
import sh.zolt.build.CompileDiagnostics;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Verifies the subtle "full compile: processor-*" build detail only fires for processor fallbacks. */
final class BuildCommandProcessorFallbackTest {
    @Test
    void reportsFullCompileDrivenByANonIsolatingProcessor() {
        assertTrue(BuildCommand.processorFullCompile(result("full", "processor-dynamic")));
        assertTrue(BuildCommand.processorFullCompile(result("full", "processor-aggregating")));
        assertTrue(BuildCommand.processorFullCompile(result("full", "processor-metadata-missing")));
    }

    @Test
    void staysSilentForNonProcessorFullCompilesIncrementalAndSkipped() {
        assertFalse(BuildCommand.processorFullCompile(result("full", "")));
        assertFalse(BuildCommand.processorFullCompile(result("full", "abi-change")));
        assertFalse(BuildCommand.processorFullCompile(result("incremental", "processor-dynamic")));
        assertFalse(BuildCommand.processorFullCompile(result("skipped", "")));
    }

    private static BuildResult result(String mode, String reason) {
        return new BuildResult(
                Optional.empty(), 1, 0, Path.of("target/classes"), "", false, mode, reason,
                CompileDiagnostics.empty(), 0L, 0L);
    }
}
