package com.zolt.release.channel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.release.ReleaseTarget;
import org.junit.jupiter.api.Test;

final class ReleaseChannelManifestValidatorTest {
    private final ReleaseChannelManifestValidator validator = new ReleaseChannelManifestValidator();

    @Test
    void validatesBetaChannelManifestForEverySupportedTarget() {
        ReleaseChannelManifest manifest = validator.validate(manifestJson("""
                {
                  "target": "macos-arm64",
                  "archive": "zolt-0.1.0-macos-arm64.tar.gz",
                  "archiveUrl": "https://dist.zolt.build/artifacts/stable/0.1.0/zolt-0.1.0-macos-arm64.tar.gz",
                  "checksumUrl": "https://dist.zolt.build/artifacts/stable/0.1.0/zolt-0.1.0-macos-arm64.tar.gz.sha256",
                  "format": "tar.gz",
                  "binaryName": "zolt"
                },
                {
                  "target": "macos-x64",
                  "archive": "zolt-0.1.0-macos-x64.tar.gz",
                  "archiveUrl": "https://dist.zolt.build/artifacts/stable/0.1.0/zolt-0.1.0-macos-x64.tar.gz",
                  "checksumUrl": "https://dist.zolt.build/artifacts/stable/0.1.0/zolt-0.1.0-macos-x64.tar.gz.sha256",
                  "format": "tar.gz",
                  "binaryName": "zolt"
                },
                {
                  "target": "linux-arm64",
                  "archive": "zolt-0.1.0-linux-arm64.tar.gz",
                  "archiveUrl": "https://dist.zolt.build/artifacts/stable/0.1.0/zolt-0.1.0-linux-arm64.tar.gz",
                  "checksumUrl": "https://dist.zolt.build/artifacts/stable/0.1.0/zolt-0.1.0-linux-arm64.tar.gz.sha256",
                  "format": "tar.gz",
                  "binaryName": "zolt"
                },
                {
                  "target": "linux-x64",
                  "archive": "zolt-0.1.0-linux-x64.tar.gz",
                  "archiveUrl": "https://dist.zolt.build/artifacts/stable/0.1.0/zolt-0.1.0-linux-x64.tar.gz",
                  "sha256": "%s",
                  "format": "tar.gz",
                  "binaryName": "zolt",
                  "signature": {
                    "kind": "minisign",
                    "url": "https://dist.zolt.build/artifacts/stable/0.1.0/zolt-0.1.0-linux-x64.tar.gz.minisig"
                  }
                },
                {
                  "target": "windows-x64",
                  "archive": "zolt-0.1.0-windows-x64.zip",
                  "archiveUrl": "https://dist.zolt.build/artifacts/stable/0.1.0/zolt-0.1.0-windows-x64.zip",
                  "checksumUrl": "https://dist.zolt.build/artifacts/stable/0.1.0/zolt-0.1.0-windows-x64.zip.sha256",
                  "format": "zip",
                  "binaryName": "zolt.exe"
                }
                """.formatted("1".repeat(64))));

        assertEquals(1, manifest.schemaVersion());
        assertEquals("stable", manifest.channel());
        assertEquals("0.1.0", manifest.version());
        assertEquals("0123456789abcdef", manifest.commit());
        assertEquals("2026-06-28T00:00:00Z", manifest.createdAt());
        assertEquals(5, manifest.artifacts().size());
        assertEquals("zolt", manifest.artifactFor(ReleaseTarget.LINUX_X64).binaryName());
        assertEquals("zolt.exe", manifest.artifactFor(ReleaseTarget.WINDOWS_X64).binaryName());
        assertEquals("minisign", manifest.artifactFor(ReleaseTarget.LINUX_X64).signature().orElseThrow().kind());
        assertTrue(manifest.artifactFor(ReleaseTarget.LINUX_X64).sha256().orElseThrow().matches("[0-9a-f]{64}"));
    }

    @Test
    void rejectsUnsupportedTargetWithActionableDiagnostic() {
        ReleaseChannelManifestException exception = assertThrows(
                ReleaseChannelManifestException.class,
                () -> validator.validate(manifestJson("""
                        {
                          "target": "solaris-sparc",
                          "archive": "zolt-0.1.0-solaris-sparc.tar.gz",
                          "archiveUrl": "https://dist.zolt.build/artifacts/stable/0.1.0/zolt-0.1.0-solaris-sparc.tar.gz",
                          "checksumUrl": "https://dist.zolt.build/artifacts/stable/0.1.0/zolt-0.1.0-solaris-sparc.tar.gz.sha256",
                          "format": "tar.gz",
                          "binaryName": "zolt"
                        }
                        """)));

        assertTrue(exception.getMessage().contains("unsupported target `solaris-sparc`"));
        assertTrue(exception.getMessage().contains("Supported targets: macos-arm64, macos-x64, linux-arm64, linux-x64, windows-x64"));
    }

    @Test
    void missingTargetForInstallerSelectionFailsClearly() {
        ReleaseChannelManifest manifest = validator.validate(manifestJson("""
                {
                  "target": "linux-x64",
                  "archive": "zolt-0.1.0-linux-x64.tar.gz",
                  "archiveUrl": "https://dist.zolt.build/artifacts/stable/0.1.0/zolt-0.1.0-linux-x64.tar.gz",
                  "checksumUrl": "https://dist.zolt.build/artifacts/stable/0.1.0/zolt-0.1.0-linux-x64.tar.gz.sha256",
                  "format": "tar.gz",
                  "binaryName": "zolt"
                }
                """));

        ReleaseChannelManifestException exception = assertThrows(
                ReleaseChannelManifestException.class,
                () -> manifest.artifactFor(ReleaseTarget.MACOS_ARM64));

        assertTrue(exception.getMessage().contains("does not include native archive target `macos-arm64`"));
        assertTrue(exception.getMessage().contains("linux-x64"));
    }

    @Test
    void malformedManifestFailsWithMissingFieldDiagnostic() {
        ReleaseChannelManifestException exception = assertThrows(
                ReleaseChannelManifestException.class,
                () -> validator.validate("""
                        {
                          "schemaVersion": 1,
                          "channel": "stable",
                          "version": "0.1.0",
                          "commit": "0123456789abcdef"
                        }
                        """));

        assertEquals("release channel manifest is missing `createdAt`.", exception.getMessage());
    }

    @Test
    void rejectsJreOrJvmArtifactShapes() {
        ReleaseChannelManifestException exception = assertThrows(
                ReleaseChannelManifestException.class,
                () -> validator.validate(manifestJson("""
                        {
                          "target": "linux-x64",
                          "archive": "zolt-0.1.0-linux-x64.jar",
                          "archiveUrl": "https://dist.zolt.build/artifacts/stable/0.1.0/zolt-0.1.0-linux-x64.jar",
                          "checksumUrl": "https://dist.zolt.build/artifacts/stable/0.1.0/zolt-0.1.0-linux-x64.jar.sha256",
                          "format": "tar.gz",
                          "binaryName": "zolt"
                        }
                        """)));

        assertTrue(exception.getMessage().contains("must reference a native tar.gz archive"));
        assertTrue(exception.getMessage().contains("not a JVM/JRE artifact"));
    }

    @Test
    void requiresChecksumUrlOrInlineSha256() {
        ReleaseChannelManifestException exception = assertThrows(
                ReleaseChannelManifestException.class,
                () -> validator.validate(manifestJson("""
                        {
                          "target": "linux-x64",
                          "archive": "zolt-0.1.0-linux-x64.tar.gz",
                          "archiveUrl": "https://dist.zolt.build/artifacts/stable/0.1.0/zolt-0.1.0-linux-x64.tar.gz",
                          "format": "tar.gz",
                          "binaryName": "zolt"
                        }
                        """)));

        assertEquals("Release channel artifact `linux-x64` must include checksumUrl or sha256.", exception.getMessage());
    }

    @Test
    void acceptsNightlyManifestVersion() {
        ReleaseChannelManifest manifest = validator.validate(manifestJson(
                "nightly",
                "0.1.0-nightly.20260628.0123456",
                """
                {
                  "target": "linux-x64",
                  "archive": "zolt-0.1.0-nightly.20260628.0123456-linux-x64.tar.gz",
                  "archiveUrl": "https://dist.zolt.build/artifacts/nightly/0.1.0-nightly.20260628.0123456/zolt-0.1.0-nightly.20260628.0123456-linux-x64.tar.gz",
                  "sha256": "%s",
                  "format": "tar.gz",
                  "binaryName": "zolt"
                }
                """.formatted("A".repeat(64))));

        assertEquals("nightly", manifest.channel());
        assertEquals("0.1.0-nightly.20260628.0123456", manifest.version());
    }

    @Test
    void rejectsUnsupportedChannel() {
        ReleaseChannelManifestException exception = assertThrows(
                ReleaseChannelManifestException.class,
                () -> validator.validate(manifestJson("dev", "0.1.0", linuxArtifact())));

        assertTrue(exception.getMessage().contains("channel"));
        assertTrue(exception.getMessage().contains("stable, nightly"));
    }

    @Test
    void rejectsUnsafeVersionSegments() {
        for (String version : new String[] {"../1.0.0", "/tmp/zolt", "C:\\\\zolt", ""}) {
            ReleaseChannelManifestException exception = assertThrows(
                    ReleaseChannelManifestException.class,
                    () -> validator.validate(manifestJson("stable", version, linuxArtifact())));

            assertTrue(exception.getMessage().contains("version"), exception.getMessage());
        }
    }

    @Test
    void rejectsUnsafeArchiveFilenames() {
        for (String archive : new String[] {
            "../zolt.tar.gz",
            "/tmp/zolt.tar.gz",
            "C:\\\\zolt.tar.gz",
            "dir/zolt.tar.gz",
            "dir\\\\zolt.tar.gz",
            ""
        }) {
            ReleaseChannelManifestException exception = assertThrows(
                    ReleaseChannelManifestException.class,
                    () -> validator.validate(manifestJson(linuxArtifact(
                            archive,
                            "https://dist.zolt.build/artifacts/stable/0.1.0/zolt-0.1.0-linux-x64.tar.gz",
                            """
                            "sha256": "%s",
                            """.formatted("1".repeat(64))))));

            assertTrue(exception.getMessage().contains("archive"), exception.getMessage());
        }
    }

    @Test
    void rejectsNonHttpsPublicArtifactUrls() {
        ReleaseChannelManifestException archiveException = assertThrows(
                ReleaseChannelManifestException.class,
                () -> validator.validate(manifestJson(linuxArtifact(
                        "zolt-0.1.0-linux-x64.tar.gz",
                        "http://dist.zolt.build/artifacts/stable/0.1.0/zolt-0.1.0-linux-x64.tar.gz",
                        """
                        "sha256": "%s",
                        """.formatted("1".repeat(64))))));
        assertTrue(archiveException.getMessage().contains("archiveUrl"));
        assertTrue(archiveException.getMessage().contains("HTTPS"));

        ReleaseChannelManifestException checksumException = assertThrows(
                ReleaseChannelManifestException.class,
                () -> validator.validate(manifestJson(linuxArtifact(
                        "zolt-0.1.0-linux-x64.tar.gz",
                        "https://dist.zolt.build/artifacts/stable/0.1.0/zolt-0.1.0-linux-x64.tar.gz",
                        """
                        "checksumUrl": "file:///tmp/zolt-0.1.0-linux-x64.tar.gz.sha256",
                        """))));
        assertTrue(checksumException.getMessage().contains("checksumUrl"));
        assertTrue(checksumException.getMessage().contains("HTTPS"));
    }

    @Test
    void rejectsMalformedSha256() {
        ReleaseChannelManifestException exception = assertThrows(
                ReleaseChannelManifestException.class,
                () -> validator.validate(manifestJson(linuxArtifact(
                        "zolt-0.1.0-linux-x64.tar.gz",
                        "https://dist.zolt.build/artifacts/stable/0.1.0/zolt-0.1.0-linux-x64.tar.gz",
                        """
                        "sha256": "not-a-sha",
                        """))));

        assertEquals("Release channel artifact `linux-x64` sha256 must be exactly 64 hexadecimal characters.", exception.getMessage());
    }

    @Test
    void rejectsUnsafeSignatureMetadata() {
        ReleaseChannelManifestException exception = assertThrows(
                ReleaseChannelManifestException.class,
                () -> validator.validate(manifestJson("""
                        {
                          "target": "linux-x64",
                          "archive": "zolt-0.1.0-linux-x64.tar.gz",
                          "archiveUrl": "https://dist.zolt.build/artifacts/stable/0.1.0/zolt-0.1.0-linux-x64.tar.gz",
                          "sha256": "%s",
                          "format": "tar.gz",
                          "binaryName": "zolt",
                          "signature": {
                            "kind": "minisign",
                            "url": "http://dist.zolt.build/artifacts/stable/0.1.0/zolt-0.1.0-linux-x64.tar.gz.minisig"
                          }
                        }
                        """.formatted("1".repeat(64)))));

        assertTrue(exception.getMessage().contains("signature.url"));
        assertTrue(exception.getMessage().contains("HTTPS"));
    }

    private static String manifestJson(String artifacts) {
        return manifestJson("stable", "0.1.0", artifacts);
    }

    private static String manifestJson(String channel, String version, String artifacts) {
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
                """.formatted(channel, version, artifacts.indent(4));
    }

    private static String linuxArtifact() {
        return linuxArtifact(
                "zolt-0.1.0-linux-x64.tar.gz",
                "https://dist.zolt.build/artifacts/stable/0.1.0/zolt-0.1.0-linux-x64.tar.gz",
                """
                "checksumUrl": "https://dist.zolt.build/artifacts/stable/0.1.0/zolt-0.1.0-linux-x64.tar.gz.sha256",
                """);
    }

    private static String linuxArtifact(String archive, String archiveUrl, String checksumOrShaField) {
        return """
                {
                  "target": "linux-x64",
                  "archive": "%s",
                  "archiveUrl": "%s",
                %s
                  "format": "tar.gz",
                  "binaryName": "zolt"
                }
                """.formatted(archive, archiveUrl, checksumOrShaField.indent(2));
    }
}
