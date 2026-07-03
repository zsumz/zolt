package sh.zolt.release.channel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import sh.zolt.release.ReleaseTarget;
import org.junit.jupiter.api.Test;

final class ReleaseChannelManifestValidatorEdgeTest {
    private final ReleaseChannelManifestValidator validator = new ReleaseChannelManifestValidator();

    @Test
    void acceptsLocalManifestFileUrlsOnlyForExplicitLocalValidation() {
        String json = manifestJson("""
                {
                  "target": "linux-x64",
                  "archive": "zolt-0.1.0-linux-x64.tar.gz",
                  "archiveUrl": "file:///tmp/zolt-0.1.0-linux-x64.tar.gz",
                  "checksumUrl": "file:///tmp/zolt-0.1.0-linux-x64.tar.gz.sha256",
                  "format": "tar.gz",
                  "binaryName": "zolt",
                  "signature": {
                    "kind": "minisign",
                    "url": "file:///tmp/zolt-0.1.0-linux-x64.tar.gz.minisig"
                  }
                }
                """);

        ReleaseChannelManifest manifest = validator.validateLocalManifest(json);
        assertEquals("file:///tmp/zolt-0.1.0-linux-x64.tar.gz", manifest.artifactFor(ReleaseTarget.LINUX_X64).archiveUrl());

        ReleaseChannelManifestException exception = assertThrows(
                ReleaseChannelManifestException.class,
                () -> validator.validate(json));
        assertTrue(exception.getMessage().contains("archiveUrl"));
        assertTrue(exception.getMessage().contains("HTTPS"));
    }

    @Test
    void rejectsMissingAndEmptyArtifactArraysWithDirectDiagnostics() {
        ReleaseChannelManifestException missing = assertThrows(
                ReleaseChannelManifestException.class,
                () -> validator.validate("""
                        {
                          "schemaVersion": 1,
                          "channel": "stable",
                          "version": "0.1.0",
                          "commit": "0123456789abcdef",
                          "createdAt": "2026-06-28T00:00:00Z"
                        }
                        """));
        ReleaseChannelManifestException empty = assertThrows(
                ReleaseChannelManifestException.class,
                () -> validator.validate("""
                        {
                          "schemaVersion": 1,
                          "channel": "stable",
                          "version": "0.1.0",
                          "commit": "0123456789abcdef",
                          "createdAt": "2026-06-28T00:00:00Z",
                          "artifacts": []
                        }
                        """));

        assertEquals("Release channel manifest is missing artifacts array.", missing.getMessage());
        assertEquals("Release channel manifest artifacts array is empty.", empty.getMessage());
    }

    @Test
    void rejectsDuplicateTargetsWithTheRepeatedTargetId() {
        ReleaseChannelManifestException exception = assertThrows(
                ReleaseChannelManifestException.class,
                () -> validator.validate(manifestJson(linuxArtifact(), linuxArtifact())));

        assertEquals("Release channel manifest repeats target `linux-x64`.", exception.getMessage());
    }

    @Test
    void rejectsCredentialedArtifactUrls() {
        ReleaseChannelManifestException exception = assertThrows(
                ReleaseChannelManifestException.class,
                () -> validator.validate(manifestJson(linuxArtifact(
                        "https://user:secret@dist.zolt.sh/artifacts/stable/0.1.0/zolt-0.1.0-linux-x64.tar.gz",
                        """
                        "sha256": "%s",
                        """.formatted("a".repeat(64))))));

        assertEquals("Release channel manifest archiveUrl must not include URL credentials.", exception.getMessage());
    }

    @Test
    void rejectsChecksumUrlThatIsNotSha256Sidecar() {
        ReleaseChannelManifestException exception = assertThrows(
                ReleaseChannelManifestException.class,
                () -> validator.validate(manifestJson(linuxArtifact(
                        "https://dist.zolt.sh/artifacts/stable/0.1.0/zolt-0.1.0-linux-x64.tar.gz",
                        """
                        "checksumUrl": "https://dist.zolt.sh/artifacts/stable/0.1.0/zolt-0.1.0-linux-x64.tar.gz.sig",
                        """))));

        assertEquals("Release channel artifact `linux-x64` checksumUrl must reference a .sha256 sidecar.", exception.getMessage());
    }

    @Test
    void rejectsFormatAndBinaryNameThatDoNotMatchTarget() {
        ReleaseChannelManifestException format = assertThrows(
                ReleaseChannelManifestException.class,
                () -> validator.validate(manifestJson("""
                        {
                          "target": "windows-x64",
                          "archive": "zolt-0.1.0-windows-x64.zip",
                          "archiveUrl": "https://dist.zolt.sh/artifacts/stable/0.1.0/zolt-0.1.0-windows-x64.zip",
                          "checksumUrl": "https://dist.zolt.sh/artifacts/stable/0.1.0/zolt-0.1.0-windows-x64.zip.sha256",
                          "format": "tar.gz",
                          "binaryName": "zolt.exe"
                        }
                        """)));
        ReleaseChannelManifestException binaryName = assertThrows(
                ReleaseChannelManifestException.class,
                () -> validator.validate(manifestJson("""
                        {
                          "target": "windows-x64",
                          "archive": "zolt-0.1.0-windows-x64.zip",
                          "archiveUrl": "https://dist.zolt.sh/artifacts/stable/0.1.0/zolt-0.1.0-windows-x64.zip",
                          "checksumUrl": "https://dist.zolt.sh/artifacts/stable/0.1.0/zolt-0.1.0-windows-x64.zip.sha256",
                          "format": "zip",
                          "binaryName": "zolt"
                        }
                        """)));

        assertTrue(format.getMessage().contains("has format `tar.gz`; expected `zip`"), format.getMessage());
        assertTrue(binaryName.getMessage().contains("has binaryName `zolt`; expected `zolt.exe`"), binaryName.getMessage());
    }

    private static String manifestJson(String... artifacts) {
        return """
                {
                  "schemaVersion": 1,
                  "channel": "stable",
                  "version": "0.1.0",
                  "commit": "0123456789abcdef",
                  "createdAt": "2026-06-28T00:00:00Z",
                  "artifacts": [
                %s
                  ]
                }
                """.formatted(String.join(",\n", artifacts).indent(4));
    }

    private static String linuxArtifact() {
        return linuxArtifact(
                "https://dist.zolt.sh/artifacts/stable/0.1.0/zolt-0.1.0-linux-x64.tar.gz",
                """
                "checksumUrl": "https://dist.zolt.sh/artifacts/stable/0.1.0/zolt-0.1.0-linux-x64.tar.gz.sha256",
                """);
    }

    private static String linuxArtifact(String archiveUrl, String checksumOrShaField) {
        return """
                {
                  "target": "linux-x64",
                  "archive": "zolt-0.1.0-linux-x64.tar.gz",
                  "archiveUrl": "%s",
                %s
                  "format": "tar.gz",
                  "binaryName": "zolt"
                }
                """.formatted(archiveUrl, checksumOrShaField.indent(2));
    }
}
