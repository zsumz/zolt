package sh.zolt.release.archive;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import sh.zolt.release.ReleaseTarget;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class ReleaseArchiveManifestTest extends ReleaseArchiveTestSupport {
    private final ReleaseArchiveService service = new ReleaseArchiveService();

    @Test
    void manifestIsDeterministicAndListsExistingArchives() throws IOException {
        writeProjectFiles();
        Path unixBinary = writeBinary("target/native/zolt");
        Path windowsBinary = writeBinary("target/native/zolt.exe");

        service.assemble(
                projectDir,
                config(),
                ReleaseTarget.WINDOWS_X64,
                windowsBinary,
                Path.of("dist"));
        ReleaseArchiveResult result = service.assemble(
                projectDir,
                config(),
                ReleaseTarget.MACOS_ARM64,
                unixBinary,
                Path.of("dist"));

        String manifest = java.nio.file.Files.readString(result.manifestPath());
        int macosIndex = manifest.indexOf("\"archive\": \"zolt-0.1.0-macos-arm64.tar.gz\"");
        int windowsIndex = manifest.indexOf("\"archive\": \"zolt-0.1.0-windows-x64.zip\"");
        assertTrue(macosIndex >= 0);
        assertTrue(windowsIndex >= 0);
        assertTrue(macosIndex < windowsIndex);
        assertTrue(java.nio.file.Files.exists(projectDir.resolve("dist/zolt-0.1.0-macos-arm64.tar.gz.sha256")));
        assertTrue(java.nio.file.Files.exists(projectDir.resolve("dist/zolt-0.1.0-windows-x64.zip.sha256")));

        service.assemble(
                projectDir,
                config(),
                ReleaseTarget.MACOS_ARM64,
                unixBinary,
                Path.of("dist"));
        assertEquals(manifest, java.nio.file.Files.readString(result.manifestPath()));
    }
}
