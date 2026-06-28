package com.zolt.release;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        ReleaseChannelManifestException exception = org.junit.jupiter.api.Assertions.assertThrows(
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

        ReleaseChannelManifestException exception = org.junit.jupiter.api.Assertions.assertThrows(
                ReleaseChannelManifestException.class,
                () -> manifest.artifactFor(ReleaseTarget.MACOS_ARM64));

        assertTrue(exception.getMessage().contains("does not include native archive target `macos-arm64`"));
        assertTrue(exception.getMessage().contains("linux-x64"));
    }

    @Test
    void malformedManifestFailsWithMissingFieldDiagnostic() {
        ReleaseChannelManifestException exception = org.junit.jupiter.api.Assertions.assertThrows(
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
        ReleaseChannelManifestException exception = org.junit.jupiter.api.Assertions.assertThrows(
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
        ReleaseChannelManifestException exception = org.junit.jupiter.api.Assertions.assertThrows(
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

    private static String manifestJson(String artifacts) {
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
                """.formatted(artifacts.indent(4));
    }
}
