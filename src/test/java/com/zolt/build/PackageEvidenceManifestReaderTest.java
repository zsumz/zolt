package com.zolt.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class PackageEvidenceManifestReaderTest {
    @TempDir
    private Path tempDir;

    private final PackageEvidenceManifestReader reader = new PackageEvidenceManifestReader();

    @Test
    void readsOwnedPackageEvidenceFields() throws IOException {
        Path manifest = tempDir.resolve("demo.jar.zolt-package.json");
        Files.writeString(manifest, """
                {
                  "schema": "zolt.package-evidence.v1",
                  "project": {
                    "name": "demo"
                  },
                  "package": {
                    "archive": "target/demo-0.1.0.jar",
                    "archiveSha256": "sha256:abc123"
                  }
                }
                """);

        PackageEvidenceManifest evidence = reader.read(manifest);

        assertEquals("zolt.package-evidence.v1", evidence.schema());
        assertEquals("target/demo-0.1.0.jar", evidence.archive());
        assertEquals("sha256:abc123", evidence.archiveSha256());
    }

    @Test
    void rejectsMalformedPackageEvidence() throws IOException {
        Path manifest = tempDir.resolve("demo.jar.zolt-package.json");
        Files.writeString(manifest, """
                {
                  "schema": "zolt.package-evidence.v1",
                  "package": {
                    "archive": "target/demo-0.1.0.jar"
                  }
                }
                """);

        PackageException exception = assertThrows(PackageException.class, () -> reader.read(manifest));

        assertTrue(exception.getMessage().contains("is missing string field `archiveSha256`"));
        assertTrue(exception.getMessage().contains("Regenerate package evidence with `zolt package`."));
    }
}
