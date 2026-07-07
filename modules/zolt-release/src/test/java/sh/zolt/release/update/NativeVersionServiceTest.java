package sh.zolt.release.update;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

final class NativeVersionServiceTest extends NativeUpdateServiceTestCase {
    private final NativeVersionService versionService = new NativeVersionService();

    @Test
    void listsInstalledVersionsAndMarksCurrent() throws IOException {
        InstalledFixture installed = install("0.1.0");
        writeFakeZolt(installed.installRoot().resolve("versions/0.1.1/bin/zolt"), "0.1.1");
        Files.createDirectories(installed.installRoot().resolve("versions/incomplete/bin"));

        NativeVersionListResult result = versionService.list(listRequest(installed));

        assertEquals("0.1.0", result.currentVersion());
        assertEquals(List.of("0.1.0", "0.1.1"), result.versions().stream()
                .map(NativeInstalledVersion::version)
                .toList());
        assertEquals(List.of(true, false), result.versions().stream()
                .map(NativeInstalledVersion::current)
                .toList());
    }

    @Test
    void switchesToInstalledVersionAndRecordsPreviousVersion() throws IOException {
        InstalledFixture installed = install("0.1.0");
        writeFakeZolt(installed.installRoot().resolve("versions/0.1.1/bin/zolt"), "0.1.1");

        NativeVersionSwitchResult result = versionService.use(switchRequest(installed, "0.1.1"));

        assertTrue(result.switched());
        assertEquals("0.1.0", result.previousVersion());
        assertEquals("0.1.1", result.currentVersion());
        assertEquals("../versions/0.1.1/bin/zolt", Files.readSymbolicLink(installed.binLink()).toString());
        assertEquals("0.1.0", Files.readString(installed.installRoot().resolve("previous-version")).strip());
    }

    @Test
    void alreadyCurrentVersionDoesNotRewriteRollbackState() throws IOException {
        InstalledFixture installed = install("0.1.0");

        NativeVersionSwitchResult result = versionService.use(switchRequest(installed, "0.1.0"));

        assertFalse(result.switched());
        assertEquals("0.1.0", result.currentVersion());
        assertFalse(Files.exists(installed.installRoot().resolve("previous-version")));
        assertEquals("../versions/0.1.0/bin/zolt", Files.readSymbolicLink(installed.binLink()).toString());
    }

    @Test
    void rollbackUsesRecordedPreviousVersionAndKeepsToggleState() throws IOException {
        InstalledFixture installed = install("0.1.0");
        writeFakeZolt(installed.installRoot().resolve("versions/0.1.1/bin/zolt"), "0.1.1");
        versionService.use(switchRequest(installed, "0.1.1"));

        NativeVersionSwitchResult result = versionService.rollback(listRequest(installed));

        assertTrue(result.switched());
        assertEquals("0.1.1", result.previousVersion());
        assertEquals("0.1.0", result.currentVersion());
        assertEquals("../versions/0.1.0/bin/zolt", Files.readSymbolicLink(installed.binLink()).toString());
        assertEquals("0.1.1", Files.readString(installed.installRoot().resolve("previous-version")).strip());
    }

    @Test
    void rollbackFailsWithoutRecordedPreviousVersion() throws IOException {
        InstalledFixture installed = install("0.1.0");

        NativeUpdateException exception = assertThrows(
                NativeUpdateException.class,
                () -> versionService.rollback(listRequest(installed)));

        assertTrue(exception.getMessage().contains("No previous native Zolt version is recorded"));
        assertEquals("../versions/0.1.0/bin/zolt", Files.readSymbolicLink(installed.binLink()).toString());
    }

    @Test
    void pruneRemovesOldestVersionsButPreservesCurrentAndPrevious() throws IOException {
        InstalledFixture installed = install("0.1.0");
        writeFakeZolt(installed.installRoot().resolve("versions/0.1.1/bin/zolt"), "0.1.1");
        writeFakeZolt(installed.installRoot().resolve("versions/0.1.2/bin/zolt"), "0.1.2");
        writeFakeZolt(installed.installRoot().resolve("versions/0.1.3/bin/zolt"), "0.1.3");
        writeFakeZolt(installed.installRoot().resolve("versions/0.1.4/bin/zolt"), "0.1.4");
        writeFakeZolt(installed.installRoot().resolve("versions/0.1.5/bin/zolt"), "0.1.5");
        writeFakeZolt(installed.installRoot().resolve("versions/0.1.6/bin/zolt"), "0.1.6");
        versionService.use(switchRequest(installed, "0.1.2"));

        NativeVersionPruneResult result = versionService.prune(pruneRequest(installed, 5, false));

        assertEquals("0.1.2", result.currentVersion());
        assertEquals("0.1.0", result.previousVersion().orElseThrow());
        assertEquals(5, result.keep());
        assertFalse(result.dryRun());
        assertEquals(List.of("0.1.1", "0.1.3"), result.prunedVersions().stream()
                .map(NativeInstalledVersion::version)
                .toList());
        assertEquals(List.of("0.1.0", "0.1.2", "0.1.4", "0.1.5", "0.1.6"), installedVersionDirectories(installed));
        assertEquals("../versions/0.1.2/bin/zolt", Files.readSymbolicLink(installed.binLink()).toString());
        assertEquals("0.1.0", Files.readString(installed.installRoot().resolve("previous-version")).strip());
    }

    @Test
    void pruneDryRunReportsVersionsWithoutDeletingThem() throws IOException {
        InstalledFixture installed = install("0.1.0");
        writeFakeZolt(installed.installRoot().resolve("versions/0.1.1/bin/zolt"), "0.1.1");
        writeFakeZolt(installed.installRoot().resolve("versions/0.1.2/bin/zolt"), "0.1.2");

        NativeVersionPruneResult result = versionService.prune(pruneRequest(installed, 1, true));

        assertTrue(result.dryRun());
        assertEquals(List.of("0.1.1", "0.1.2"), result.prunedVersions().stream()
                .map(NativeInstalledVersion::version)
                .toList());
        assertEquals(List.of("0.1.0", "0.1.1", "0.1.2"), installedVersionDirectories(installed));
    }

    @Test
    void pruneKeepCountCannotDeleteCurrentOrPreviousVersion() throws IOException {
        InstalledFixture installed = install("0.1.0");
        writeFakeZolt(installed.installRoot().resolve("versions/0.1.1/bin/zolt"), "0.1.1");
        writeFakeZolt(installed.installRoot().resolve("versions/0.1.2/bin/zolt"), "0.1.2");
        versionService.use(switchRequest(installed, "0.1.1"));

        NativeVersionPruneResult result = versionService.prune(pruneRequest(installed, 1, false));

        assertEquals(List.of("0.1.0", "0.1.1"), result.keptVersions().stream()
                .map(NativeInstalledVersion::version)
                .toList());
        assertEquals(List.of("0.1.2"), result.prunedVersions().stream()
                .map(NativeInstalledVersion::version)
                .toList());
        assertEquals(List.of("0.1.0", "0.1.1"), installedVersionDirectories(installed));
    }

    @Test
    void pruneRejectsNonPositiveKeepCount() throws IOException {
        InstalledFixture installed = install("0.1.0");

        NativeUpdateException exception = assertThrows(
                NativeUpdateException.class,
                () -> versionService.prune(pruneRequest(installed, 0, false)));

        assertTrue(exception.getMessage().contains("keep count must be at least 1"));
        assertEquals(List.of("0.1.0"), installedVersionDirectories(installed));
    }

    @Test
    void unsafeVersionCannotEscapeVersionsDirectory() throws IOException {
        InstalledFixture installed = install("0.1.0");

        NativeUpdateException exception = assertThrows(
                NativeUpdateException.class,
                () -> versionService.use(switchRequest(installed, "../0.1.1")));

        assertTrue(exception.getMessage().contains("one installed version path segment"));
        assertEquals("../versions/0.1.0/bin/zolt", Files.readSymbolicLink(installed.binLink()).toString());
    }

    @Test
    void symlinkedVersionDirectoryIsNotUsable() throws IOException {
        InstalledFixture installed = install("0.1.0");
        Path outside = tempDir.resolve("outside-version");
        writeFakeZolt(outside.resolve("bin/zolt"), "0.1.1");
        createSymlink(installed.installRoot().resolve("versions/0.1.1"), outside);

        NativeVersionListResult list = versionService.list(listRequest(installed));
        NativeUpdateException exception = assertThrows(
                NativeUpdateException.class,
                () -> versionService.use(switchRequest(installed, "0.1.1")));

        assertEquals(List.of("0.1.0"), list.versions().stream()
                .map(NativeInstalledVersion::version)
                .toList());
        assertTrue(exception.getMessage().contains("is not installed"));
        assertEquals("../versions/0.1.0/bin/zolt", Files.readSymbolicLink(installed.binLink()).toString());
    }

    @Test
    void smokeFailureKeepsCurrentVersion() throws IOException {
        InstalledFixture installed = install("0.1.0");
        writeFakeZolt(installed.installRoot().resolve("versions/0.1.1/bin/zolt"), "not-0.1.1");

        NativeUpdateException exception = assertThrows(
                NativeUpdateException.class,
                () -> versionService.use(switchRequest(installed, "0.1.1")));

        assertTrue(exception.getMessage().contains("failed smoke verification"));
        assertFalse(Files.exists(installed.installRoot().resolve("previous-version")));
        assertEquals("../versions/0.1.0/bin/zolt", Files.readSymbolicLink(installed.binLink()).toString());
    }

    private static NativeVersionListRequest listRequest(InstalledFixture installed) {
        return new NativeVersionListRequest(installed.installRoot(), installed.binLink());
    }

    private static NativeVersionSwitchRequest switchRequest(InstalledFixture installed, String version) {
        return new NativeVersionSwitchRequest(installed.installRoot(), installed.binLink(), version);
    }

    private static NativeVersionPruneRequest pruneRequest(InstalledFixture installed, int keep, boolean dryRun) {
        return new NativeVersionPruneRequest(installed.installRoot(), installed.binLink(), keep, dryRun);
    }

    private static List<String> installedVersionDirectories(InstalledFixture installed) throws IOException {
        try (var stream = Files.list(installed.installRoot().resolve("versions"))) {
            return stream
                    .filter(path -> Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS))
                    .map(path -> path.getFileName().toString())
                    .sorted()
                    .toList();
        }
    }

    private static void createSymlink(Path link, Path target) throws IOException {
        try {
            Files.createSymbolicLink(link, target);
        } catch (UnsupportedOperationException | IOException exception) {
            assumeTrue(false, "symbolic links are unavailable: " + exception.getMessage());
        }
    }
}
