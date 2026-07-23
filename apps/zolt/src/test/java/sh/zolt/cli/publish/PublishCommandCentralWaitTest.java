package sh.zolt.cli.publish;

import static sh.zolt.cli.CliTestSupport.execute;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import sh.zolt.cli.CliTestSupport.CommandResult;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Guards for the --wait / --wait-timeout options; these fire before any network access. */
final class PublishCommandCentralWaitTest {
    @TempDir
    private Path tempDir;

    @Test
    void waitWithoutCentralIsRejected() {
        CommandResult result = execute("publish", "--wait", "--cwd", tempDir.toString());

        assertEquals(1, result.exitCode());
        assertTrue(result.stderr().contains("applies only to a live Maven Central publish"), result.stderr());
    }

    @Test
    void waitWithDryRunIsRejected() {
        CommandResult result = execute("publish", "--wait", "--dry-run", "--central", "--cwd", tempDir.toString());

        assertEquals(1, result.exitCode());
        assertTrue(result.stderr().contains("applies only to a live Maven Central publish"), result.stderr());
    }

    @Test
    void sbomWithWorkspaceIsRejected() {
        CommandResult result = execute("publish", "--workspace", "--sbom", "--cwd", tempDir.toString());

        assertEquals(1, result.exitCode());
        assertTrue(result.stderr().contains("--sbom is not yet supported with --workspace"), result.stderr());
    }

    @Test
    void nonPositiveWaitTimeoutIsRejected() {
        CommandResult result = execute(
                "publish", "--wait", "--central", "--wait-timeout", "0", "--cwd", tempDir.toString());

        assertEquals(1, result.exitCode());
        assertTrue(result.stderr().contains("--wait-timeout must be a positive number of seconds"), result.stderr());
    }
}
