package com.zolt.release.update;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.release.ReleaseTarget;
import com.zolt.release.channel.ReleaseChannelManifestException;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class NativeUpdateNoticeServiceTest {
    @TempDir
    private Path tempDir;

    private final NativeUpdateNoticeService service = new NativeUpdateNoticeService();
    private final Instant now = Instant.parse("2026-06-28T00:00:00Z");

    @Test
    void reportsNewerNativeVersionForInstallerManagedLayout() throws IOException {
        InstalledFixture installed = install("0.1.0");
        Path channel = writeChannel("stable", "0.1.1");

        Optional<NativeUpdateNotice> notice = service.check(request(installed, channel.toUri(), now));

        assertTrue(notice.isPresent());
        assertEquals("stable", notice.orElseThrow().channel());
        assertEquals("0.1.0", notice.orElseThrow().currentVersion());
        assertEquals("0.1.1", notice.orElseThrow().availableVersion());
        assertEquals(
                "A newer Zolt is available on stable: 0.1.0 -> 0.1.1. Download and verify the latest native archive for this channel.",
                notice.orElseThrow().message());
    }

    @Test
    void suppressesWhenInstalledVersionMatchesChannel() throws IOException {
        InstalledFixture installed = install("0.1.0");
        Path channel = writeChannel("stable", "0.1.0");

        Optional<NativeUpdateNotice> notice = service.check(request(installed, channel.toUri(), now));

        assertTrue(notice.isEmpty());
    }

    @Test
    void suppressesUnsupportedLayoutsWithoutFailingOriginalCommand() throws IOException {
        InstalledFixture installed = install("0.1.0");
        Path channel = writeChannel("stable", "0.1.1");
        Path developmentExecutable = tempDir.resolve("dev/zolt");
        Files.createDirectories(developmentExecutable.getParent());
        Files.writeString(developmentExecutable, "dev");

        Optional<NativeUpdateNotice> notice = service.check(new NativeUpdateNoticeRequest(
                installed.installRoot(),
                developmentExecutable,
                channel.toUri(),
                ReleaseTarget.LINUX_X64,
                tempDir.resolve("state"),
                now,
                Duration.ZERO,
                false,
                false,
                false,
                true));

        assertTrue(notice.isEmpty());
    }

    @Test
    void suppressesDisabledOfflineCiAndNonInteractiveChecks() throws IOException {
        InstalledFixture installed = install("0.1.0");
        URI channel = writeChannel("stable", "0.1.1").toUri();

        assertTrue(service.check(request(installed, channel, now, true, false, false, true)).isEmpty());
        assertTrue(service.check(request(installed, channel, now, false, true, false, true)).isEmpty());
        assertTrue(service.check(request(installed, channel, now, false, false, true, true)).isEmpty());
        assertTrue(service.check(request(installed, channel, now, false, false, false, false)).isEmpty());
    }

    @Test
    void suppressesInvalidChannelUrisBeforeCachingNoticeState() throws IOException {
        InstalledFixture installed = install("0.1.0");
        Path state = tempDir.resolve("state");

        NativeUpdateException exception = assertThrows(
                NativeUpdateException.class,
                () -> service.check(request(
                        installed,
                        URI.create("http://dist.example/channel.json"),
                        now,
                        state)));

        assertTrue(exception.getMessage().contains("Release channel URL"), exception.getMessage());
        assertTrue(Files.notExists(state.resolve("update-check.properties")));
    }

    @Test
    void usesCachedLatestVersionInsideCheckInterval() throws IOException {
        InstalledFixture installed = install("0.1.0");
        Path channel = writeChannel("stable", "0.1.1");
        Path state = tempDir.resolve("state");

        Optional<NativeUpdateNotice> first = service.check(request(installed, channel.toUri(), now, state));
        Files.writeString(channel, "not json");
        Optional<NativeUpdateNotice> second = service.check(request(
                installed,
                channel.toUri(),
                now.plus(Duration.ofMinutes(5)),
                state,
                Duration.ofHours(24)));

        assertTrue(first.isPresent());
        assertTrue(second.isPresent());
        assertTrue(second.orElseThrow().cached());
        assertEquals("0.1.1", second.orElseThrow().availableVersion());
    }

    @Test
    void networkFailureDoesNotFailOrPrintWithoutCache() throws IOException {
        InstalledFixture installed = install("0.1.0");

        Optional<NativeUpdateNotice> notice = service.check(request(
                installed,
                tempDir.resolve("missing-channel.json").toUri(),
                now));

        assertTrue(notice.isEmpty());
    }

    private NativeUpdateNoticeRequest request(InstalledFixture installed, URI channel, Instant time) {
        return request(installed, channel, time, tempDir.resolve("state"));
    }

    private NativeUpdateNoticeRequest request(
            InstalledFixture installed,
            URI channel,
            Instant time,
            Path stateDirectory) {
        return request(installed, channel, time, stateDirectory, Duration.ZERO);
    }

    private NativeUpdateNoticeRequest request(
            InstalledFixture installed,
            URI channel,
            Instant time,
            Path stateDirectory,
            Duration interval) {
        return new NativeUpdateNoticeRequest(
                installed.installRoot(),
                installed.binLink(),
                channel,
                ReleaseTarget.LINUX_X64,
                stateDirectory,
                time,
                interval,
                false,
                false,
                false,
                true);
    }

    private NativeUpdateNoticeRequest request(
            InstalledFixture installed,
            URI channel,
            Instant time,
            boolean disabled,
            boolean offline,
            boolean ci,
            boolean interactive) {
        return new NativeUpdateNoticeRequest(
                installed.installRoot(),
                installed.binLink(),
                channel,
                ReleaseTarget.LINUX_X64,
                tempDir.resolve("state"),
                time,
                Duration.ZERO,
                disabled,
                offline,
                ci,
                interactive);
    }

    private InstalledFixture install(String version) throws IOException {
        Path installRoot = tempDir.resolve("home/.zolt");
        Path executable = installRoot.resolve("versions").resolve(version).resolve("bin/zolt");
        Files.createDirectories(executable.getParent());
        Files.writeString(executable, "native");
        executable.toFile().setExecutable(true);
        Path bin = installRoot.resolve("bin");
        Files.createDirectories(bin);
        Path binLink = bin.resolve("zolt");
        Files.deleteIfExists(binLink);
        Files.createSymbolicLink(binLink, Path.of("../versions", version, "bin", "zolt"));
        return new InstalledFixture(installRoot, binLink);
    }

    private Path writeChannel(String channel, String version) throws IOException {
        Path path = tempDir.resolve(channel + "-" + version + ".json");
        Files.writeString(path, """
                {
                  "schemaVersion": 1,
                  "channel": "%s",
                  "version": "%s",
                  "commit": "0123456789abcdef",
                  "createdAt": "2026-06-28T00:00:00Z",
                  "artifacts": [
                    {
                      "target": "linux-x64",
                      "archive": "zolt-%s-linux-x64.tar.gz",
                      "archiveUrl": "https://dist.zolt.build/artifacts/%s/%s/zolt-%s-linux-x64.tar.gz",
                      "checksumUrl": "https://dist.zolt.build/artifacts/%s/%s/zolt-%s-linux-x64.tar.gz.sha256",
                      "format": "tar.gz",
                      "binaryName": "zolt"
                    }
                  ]
                }
                """.formatted(
                        channel,
                        version,
                        version,
                        channel,
                        version,
                        version,
                        channel,
                        version,
                        version));
        return path;
    }

    private record InstalledFixture(Path installRoot, Path binLink) {
    }
}
