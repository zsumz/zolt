package sh.zolt.release.archive;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ReleaseArchiveManifestWriterTest {
    private final ReleaseArchiveManifestWriter writer = new ReleaseArchiveManifestWriter();

    @TempDir
    private Path tempDir;

    @Test
    void writesChecksumSidecarForArchive() throws IOException {
        Path archive = tempDir.resolve("zolt-0.1.0-linux-x64.tar.gz");
        Files.writeString(archive, "archive\n");

        String checksum = writer.checksum(archive);
        Path checksumPath = writer.writeChecksum(archive, checksum);

        assertEquals(64, checksum.length());
        assertEquals(
                checksum + "  zolt-0.1.0-linux-x64.tar.gz\n",
                Files.readString(checksumPath));
    }

    @Test
    void manifestListsExistingArchivesDeterministicallyAndRefreshesChecksums() throws IOException {
        Files.writeString(tempDir.resolve("zolt-0.1.0-windows-x64.zip"), "windows\n");
        Files.writeString(tempDir.resolve("zolt-0.1.0-linux-x64.tar.gz"), "linux\n");

        Path manifest = writer.writeManifest(tempDir, "zolt", "0.1.0");

        String content = Files.readString(manifest);
        int linux = content.indexOf("\"archive\": \"zolt-0.1.0-linux-x64.tar.gz\"");
        int windows = content.indexOf("\"archive\": \"zolt-0.1.0-windows-x64.zip\"");
        assertTrue(linux >= 0);
        assertTrue(windows >= 0);
        assertTrue(linux < windows);
        assertTrue(content.contains("\"target\": \"linux-x64\""));
        assertTrue(content.contains("\"format\": \"tar.gz\""));
        assertTrue(content.contains("\"target\": \"windows-x64\""));
        assertTrue(content.contains("\"format\": \"zip\""));
        assertTrue(Files.exists(tempDir.resolve("zolt-0.1.0-linux-x64.tar.gz.sha256")));
        assertTrue(Files.exists(tempDir.resolve("zolt-0.1.0-windows-x64.zip.sha256")));

        assertEquals(content, Files.readString(writer.writeManifest(tempDir, "zolt", "0.1.0")));
    }
}
