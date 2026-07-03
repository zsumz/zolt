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

    @Test
    void rejectsEmptyMissingAndUnsupportedSchemaVersions() {
        ReleaseChannelManifestException nullManifest = assertThrows(
                ReleaseChannelManifestException.class,
                () -> validator.validate(null));
        ReleaseChannelManifestException blankManifest = assertThrows(
                ReleaseChannelManifestException.class,
                () -> validator.validate("   \n"));
        ReleaseChannelManifestException missingSchema = assertThrows(
                ReleaseChannelManifestException.class,
                () -> validator.validate("""
                        {
                          "channel": "stable",
                          "version": "0.1.0",
                          "commit": "0123456789abcdef",
                          "createdAt": "2026-06-28T00:00:00Z",
                          "artifacts": []
                        }
                        """));
        ReleaseChannelManifestException tooLargeSchema = assertThrows(
                ReleaseChannelManifestException.class,
                () -> validator.validate("""
                        {
                          "schemaVersion": 999999999999999999999,
                          "channel": "stable",
                          "version": "0.1.0",
                          "commit": "0123456789abcdef",
                          "createdAt": "2026-06-28T00:00:00Z",
                          "artifacts": []
                        }
                        """));
        ReleaseChannelManifestException unsupportedSchema = assertThrows(
                ReleaseChannelManifestException.class,
                () -> validator.validate("""
                        {
                          "schemaVersion": 2,
                          "channel": "stable",
                          "version": "0.1.0",
                          "commit": "0123456789abcdef",
                          "createdAt": "2026-06-28T00:00:00Z",
                          "artifacts": []
                        }
                        """));

        assertEquals("Release channel manifest is empty.", nullManifest.getMessage());
        assertEquals("Release channel manifest is empty.", blankManifest.getMessage());
        assertEquals("release channel manifest is missing `schemaVersion`.", missingSchema.getMessage());
        assertEquals("release channel manifest has invalid `schemaVersion`.", tooLargeSchema.getMessage());
        assertEquals("Release channel manifest has unsupported schemaVersion 2; expected 1.", unsupportedSchema.getMessage());
    }

    @Test
    void rejectsMalformedArtifactArrayShapes() {
        ReleaseChannelManifestException notArray = assertThrows(
                ReleaseChannelManifestException.class,
                () -> validator.validate("""
                        {
                          "schemaVersion": 1,
                          "channel": "stable",
                          "version": "0.1.0",
                          "commit": "0123456789abcdef",
                          "createdAt": "2026-06-28T00:00:00Z",
                          "artifacts": {}
                        }
                        """));
        ReleaseChannelManifestException unclosedArray = assertThrows(
                ReleaseChannelManifestException.class,
                () -> validator.validate("""
                        {
                          "schemaVersion": 1,
                          "channel": "stable",
                          "version": "0.1.0",
                          "commit": "0123456789abcdef",
                          "createdAt": "2026-06-28T00:00:00Z",
                          "artifacts": [
                            {
                              "target": "linux-x64"
                            }
                        }
                        """));
        ReleaseChannelManifestException unclosedObject = assertThrows(
                ReleaseChannelManifestException.class,
                () -> validator.validate("""
                        {
                          "schemaVersion": 1,
                          "channel": "stable",
                          "version": "0.1.0",
                          "commit": "0123456789abcdef",
                          "createdAt": "2026-06-28T00:00:00Z",
                          "artifacts": [
                            {
                              "target": "linux-x64"
                          ]
                        }
                        """));

        assertEquals("Release channel manifest is missing artifacts array.", notArray.getMessage());
        assertEquals("Release channel manifest is missing artifacts array.", unclosedArray.getMessage());
        assertEquals("Release channel manifest artifacts array is empty.", unclosedObject.getMessage());
    }

    @Test
    void unescapesJsonStringValuesUsedByManifestFields() {
        ReleaseChannelManifest manifest = validator.validate("""
                {
                  "schemaVersion": 1,
                  "channel": "stable",
                  "version": "0.1.0",
                  "commit": "quote\\\" slash\\/ backslash\\\\ backspace\\b form\\f newline\\n return\\r tab\\t custom\\q",
                  "createdAt": "2026-06-28T00:00:00Z",
                  "artifacts": [
                    {
                      "target": "linux-x64",
                      "archive": "zolt-0.1.0-linux-x64.tar.gz",
                      "archiveUrl": "https:\\/\\/dist.zolt.sh\\/artifacts\\/stable\\/0.1.0\\/zolt-0.1.0-linux-x64.tar.gz",
                      "checksumUrl": "https:\\/\\/dist.zolt.sh\\/artifacts\\/stable\\/0.1.0\\/zolt-0.1.0-linux-x64.tar.gz.sha256",
                      "format": "tar.gz",
                      "binaryName": "zolt"
                    }
                  ]
                }
                """);

        assertEquals("quote\" slash/ backslash\\ backspace\b form\f newline\n return\r tab\t customq", manifest.commit());
        assertEquals(
                "https://dist.zolt.sh/artifacts/stable/0.1.0/zolt-0.1.0-linux-x64.tar.gz",
                manifest.artifactFor(ReleaseTarget.LINUX_X64).archiveUrl());
    }

    @Test
    void rejectsSafeSegmentsAndUrlsThatFailSpecificConstraints() {
        ReleaseChannelManifestException invalidVersion = assertThrows(
                ReleaseChannelManifestException.class,
                () -> validator.validate(manifestJsonForVersion("stable", "release-1", linuxArtifact())));
        ReleaseChannelManifestException whitespaceVersion = assertThrows(
                ReleaseChannelManifestException.class,
                () -> validator.validate(manifestJsonForVersion("stable", " 0.1.0", linuxArtifact())));
        ReleaseChannelManifestException colonVersion = assertThrows(
                ReleaseChannelManifestException.class,
                () -> validator.validate(manifestJsonForVersion("stable", "C:zolt", linuxArtifact())));
        ReleaseChannelManifestException invalidArchiveName = assertThrows(
                ReleaseChannelManifestException.class,
                () -> validator.validate(manifestJson("""
                        {
                          "target": "linux-x64",
                          "archive": "zolt+0.1.0-linux-x64.tar.gz",
                          "archiveUrl": "https://dist.zolt.sh/artifacts/stable/0.1.0/zolt+0.1.0-linux-x64.tar.gz",
                          "sha256": "%s",
                          "format": "tar.gz",
                          "binaryName": "zolt"
                        }
                        """.formatted("a".repeat(64)))));
        ReleaseChannelManifestException missingHost = assertThrows(
                ReleaseChannelManifestException.class,
                () -> validator.validate(manifestJson(linuxArtifact(
                        "https:///artifacts/stable/0.1.0/zolt-0.1.0-linux-x64.tar.gz",
                        """
                        "sha256": "%s",
                        """.formatted("a".repeat(64))))));
        ReleaseChannelManifestException invalidUri = assertThrows(
                ReleaseChannelManifestException.class,
                () -> validator.validate(manifestJson(linuxArtifact(
                        "https://dist zolt.sh/artifacts/stable/0.1.0/zolt-0.1.0-linux-x64.tar.gz",
                        """
                        "sha256": "%s",
                        """.formatted("a".repeat(64))))));

        assertTrue(invalidVersion.getMessage().contains("must look like 0.1.0"), invalidVersion.getMessage());
        assertTrue(whitespaceVersion.getMessage().contains("one safe path segment"), whitespaceVersion.getMessage());
        assertTrue(colonVersion.getMessage().contains("one safe path segment"), colonVersion.getMessage());
        assertTrue(invalidArchiveName.getMessage().contains("archive must be a filename"), invalidArchiveName.getMessage());
        assertEquals("Release channel manifest archiveUrl must be a valid HTTPS URL.", missingHost.getMessage());
        assertEquals("Release channel manifest archiveUrl must be a valid HTTPS URL.", invalidUri.getMessage());
    }

    @Test
    void rejectsSignatureKindWithUnsafeCharacters() {
        ReleaseChannelManifestException exception = assertThrows(
                ReleaseChannelManifestException.class,
                () -> validator.validate(manifestJson("""
                        {
                          "target": "linux-x64",
                          "archive": "zolt-0.1.0-linux-x64.tar.gz",
                          "archiveUrl": "https://dist.zolt.sh/artifacts/stable/0.1.0/zolt-0.1.0-linux-x64.tar.gz",
                          "sha256": "%s",
                          "format": "tar.gz",
                          "binaryName": "zolt",
                          "signature": {
                            "kind": "mini/sign",
                            "url": "https://dist.zolt.sh/artifacts/stable/0.1.0/zolt-0.1.0-linux-x64.tar.gz.minisig"
                          }
                        }
                        """.formatted("a".repeat(64)))));

        assertEquals(
                "Release channel signature kind must use letters, digits, dots, underscores, and hyphens.",
                exception.getMessage());
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

    private static String manifestJsonForVersion(String channel, String version, String... artifacts) {
        return """
                {
                  "schemaVersion": 1,
                  "channel": "%s",
                  "version": "%s",
                  "commit": "0123456789abcdef",
                  "createdAt": "2026-06-28T00:00:00Z",
                  "artifacts": [
                %s
                  ]
                }
                """.formatted(channel, version, String.join(",\n", artifacts).indent(4));
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
