package sh.zolt.cli.toolchain;

import static sh.zolt.cli.CliTestSupport.execute;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ShimsCommandTest {
    @TempDir
    private Path tempDir;

    @Test
    void installsStatusesAndUninstallsJavaToolchainShims() throws IOException {
        Path shimsDir = tempDir.resolve("shims");

        var install = execute("shims", "install", "--shims-dir", shimsDir.toString());
        var status = execute("shims", "status", "--shims-dir", shimsDir.toString());

        assertEquals(0, install.exitCode(), install.stderr());
        assertTrue(install.stdout().contains("Installed Java toolchain shims"));
        assertShim(shimsDir.resolve("java"), "java");
        assertShim(shimsDir.resolve("javac"), "javac");
        assertShim(shimsDir.resolve("native-image"), "native-image");
        assertEquals(0, status.exitCode(), status.stderr());
        assertTrue(status.stdout().contains("java: installed"));
        assertTrue(status.stdout().contains("native-image: installed"));
        var uninstall = execute("shims", "uninstall", "--shims-dir", shimsDir.toString());
        assertEquals(0, uninstall.exitCode(), uninstall.stderr());
        assertTrue(uninstall.stdout().contains("Removed 6 Java toolchain shims"));
        assertFalse(Files.exists(shimsDir.resolve("java")));
    }

    private static void assertShim(Path path, String tool) throws IOException {
        assertTrue(Files.isExecutable(path));
        String content = Files.readString(path);
        assertTrue(content.contains("zolt exec -- " + tool));
    }
}
