package com.zolt.release;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class ReleaseArchivePathAcceptanceTest extends ReleaseArchiveTestSupport {
    private final ReleaseArchiveService service = new ReleaseArchiveService();

    @Test
    void acceptsAbsoluteReleaseBinaryInsideProject() throws IOException {
        writeProjectFiles();
        writeBinary("target/native/zolt");
        Path binary = projectDir.resolve("target/native/zolt").toAbsolutePath().normalize();

        ReleaseArchiveResult result = service.assemble(
                projectDir,
                config(),
                ReleaseTarget.LINUX_X64,
                binary,
                Path.of("dist"));

        assertEquals(projectDir.resolve("dist/zolt-0.1.0-linux-x64.tar.gz"), result.archivePath());
        assertTrue(tarEntries(result.archivePath()).contains("zolt-0.1.0-linux-x64/bin/zolt"));
    }

    @Test
    void acceptsAbsoluteReleaseOutputInsideProject() throws IOException {
        writeProjectFiles();
        Path binary = writeBinary("target/native/zolt");
        Path output = projectDir.resolve("target/native-smoke/release").toAbsolutePath().normalize();

        ReleaseArchiveResult result = service.assemble(
                projectDir,
                config(),
                ReleaseTarget.LINUX_X64,
                binary,
                output);

        assertEquals(output.resolve("zolt-0.1.0-linux-x64.tar.gz"), result.archivePath());
        assertEquals(output.resolve("zolt-0.1.0-linux-x64.tar.gz.sha256"), result.checksumPath());
        assertEquals(output.resolve("release-manifest.json"), result.manifestPath());
        assertTrue(tarEntries(result.archivePath()).contains("zolt-0.1.0-linux-x64/bin/zolt"));
    }
}
