package sh.zolt.release.update;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;

final class NativeVersionExecServiceTest extends NativeUpdateServiceTestCase {
    private final NativeVersionExecService execService = new NativeVersionExecService();

    @Test
    void plansInstalledVersionExecAndStripsOptionalLeadingZolt() throws IOException {
        InstalledFixture installed = install("0.1.0");
        writeFakeZolt(installed.installRoot().resolve("versions/0.2.0/bin/zolt"), "0.2.0");

        NativeVersionExecPlan plan = execService.plan(new NativeVersionExecRequest(
                installed.installRoot(),
                installed.binLink(),
                "0.2.0",
                List.of("zolt", "test", "--workspace")));

        assertEquals("0.2.0", plan.version());
        assertEquals(
                installed.installRoot().resolve("versions/0.2.0/bin/zolt").toRealPath(),
                plan.executable().toRealPath());
        assertEquals(List.of("test", "--workspace"), plan.arguments());
    }

    @Test
    void preservesCommandsWithoutLeadingZolt() throws IOException {
        InstalledFixture installed = install("0.1.0");
        writeFakeZolt(installed.installRoot().resolve("versions/0.2.0/bin/zolt"), "0.2.0");

        NativeVersionExecPlan plan = execService.plan(new NativeVersionExecRequest(
                installed.installRoot(),
                installed.binLink(),
                "0.2.0",
                List.of("--version")));

        assertEquals(List.of("--version"), plan.arguments());
    }

    @Test
    void missingVersionFailsActionably() throws IOException {
        InstalledFixture installed = install("0.1.0");

        NativeUpdateException exception = assertThrows(
                NativeUpdateException.class,
                () -> execService.plan(new NativeVersionExecRequest(
                        installed.installRoot(),
                        installed.binLink(),
                        "0.2.0",
                        List.of("zolt", "test"))));

        assertEquals(
                "Native Zolt version `0.2.0` is not installed under "
                        + installed.installRoot().resolve("versions").toRealPath()
                        + ".",
                exception.getMessage());
    }

    @Test
    void emptyCommandFailsActionably() throws IOException {
        InstalledFixture installed = install("0.1.0");
        writeFakeZolt(installed.installRoot().resolve("versions/0.2.0/bin/zolt"), "0.2.0");

        NativeUpdateException exception = assertThrows(
                NativeUpdateException.class,
                () -> execService.plan(new NativeVersionExecRequest(
                        installed.installRoot(),
                        installed.binLink(),
                        "0.2.0",
                        List.of("zolt"))));

        assertEquals("Native Zolt exec requires a command after `--`.", exception.getMessage());
    }
}
