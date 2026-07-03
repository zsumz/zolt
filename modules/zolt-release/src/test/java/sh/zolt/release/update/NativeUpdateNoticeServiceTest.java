package sh.zolt.release.update;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import sh.zolt.release.ReleaseTarget;
import sh.zolt.release.channel.ReleaseChannelManifestException;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Properties;
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
    void nullCheckIntervalUsesDefaultIntervalAndCachedNotice() throws IOException {
        InstalledFixture installed = install("0.1.0");
        URI channel = tempDir.resolve("missing-channel.json").toUri();
        Path state = tempDir.resolve("state");
        writeCache(
                state,
                channel,
                "linux-x64",
                "stable",
                "0.1.2",
                now.minus(Duration.ofMinutes(5)).toString());

        Optional<NativeUpdateNotice> notice = service.check(new NativeUpdateNoticeRequest(
                installed.installRoot(),
                installed.binLink(),
                channel,
                ReleaseTarget.LINUX_X64,
                state,
                now,
                null,
                false,
                false,
                false,
                true));

        assertTrue(notice.isPresent());
        assertTrue(notice.orElseThrow().cached());
        assertEquals("0.1.2", notice.orElseThrow().availableVersion());
    }

    @Test
    void dueCachedCheckRefreshesFromReadableManifest() throws IOException {
        InstalledFixture installed = install("0.1.0");
        Path channel = writeChannel("stable", "0.1.1");
        Path state = tempDir.resolve("state");
        writeCache(
                state,
                channel.toUri(),
                "linux-x64",
                "stable",
                "0.1.0",
                now.minus(Duration.ofHours(25)).toString());

        Optional<NativeUpdateNotice> notice = service.check(request(
                installed,
                channel.toUri(),
                now,
                state,
                Duration.ofHours(24)));

        assertTrue(notice.isPresent());
        assertFalse(notice.orElseThrow().cached());
        assertEquals("0.1.1", notice.orElseThrow().availableVersion());
    }

    @Test
    void staleCachedNoticeWithoutLatestVersionOrChannelIsSuppressed() throws IOException {
        InstalledFixture installed = install("0.1.0");
        URI channel = tempDir.resolve("missing-channel.json").toUri();
        Path missingLatest = tempDir.resolve("missing-latest-state");
        Path missingChannel = tempDir.resolve("missing-channel-state");
        writeCache(
                missingLatest,
                channel,
                "linux-x64",
                "stable",
                "",
                now.minus(Duration.ofMinutes(5)).toString());
        writeCache(
                missingChannel,
                channel,
                "linux-x64",
                "",
                "0.1.2",
                now.minus(Duration.ofMinutes(5)).toString());

        Optional<NativeUpdateNotice> first = service.check(request(
                installed,
                channel,
                now,
                missingLatest,
                Duration.ofHours(24)));
        Optional<NativeUpdateNotice> second = service.check(request(
                installed,
                channel,
                now,
                missingChannel,
                Duration.ofHours(24)));

        assertTrue(first.isEmpty());
        assertTrue(second.isEmpty());
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

    @Test
    void fallsBackToCachedNoticeWhenDueCheckCannotReadManifest() throws IOException {
        InstalledFixture installed = install("0.1.0");
        URI missingChannel = tempDir.resolve("missing-channel.json").toUri();
        Path state = tempDir.resolve("state");
        writeCache(
                state,
                missingChannel,
                "linux-x64",
                "stable",
                "0.1.2",
                "not-an-instant");

        Optional<NativeUpdateNotice> notice = service.check(request(
                installed,
                missingChannel,
                now,
                state,
                Duration.ofHours(24)));

        assertTrue(notice.isPresent());
        assertTrue(notice.orElseThrow().cached());
        assertEquals("0.1.2", notice.orElseThrow().availableVersion());
    }

    @Test
    void cachedNoticeInsideIntervalMustMatchChannelUriAndTarget() throws IOException {
        InstalledFixture installed = install("0.1.0");
        URI channel = tempDir.resolve("channel.json").toUri();
        Path state = tempDir.resolve("state");
        writeCache(
                state,
                channel,
                "macos-arm64",
                "stable",
                "0.1.2",
                now.minus(Duration.ofMinutes(5)).toString());

        Optional<NativeUpdateNotice> notice = service.check(request(
                installed,
                channel,
                now,
                state,
                Duration.ofHours(24)));

        assertTrue(notice.isEmpty());
    }

    @Test
    void comparesMissingVersionPartsAndTextQualifiers() throws IOException {
        InstalledFixture shortVersion = install("0.1");
        Path numericChannel = writeChannel("stable", "0.1.1");
        Optional<NativeUpdateNotice> numericNotice = service.check(request(
                shortVersion,
                numericChannel.toUri(),
                now,
                tempDir.resolve("numeric-state")));

        InstalledFixture qualifiedVersion = install("0.1.0-beta");
        Path qualifiedChannel = writeChannel("stable", "0.1.0-rc");
        Optional<NativeUpdateNotice> qualifiedNotice = service.check(request(
                qualifiedVersion,
                qualifiedChannel.toUri(),
                now,
                tempDir.resolve("qualified-state")));

        assertTrue(numericNotice.isPresent());
        assertEquals("0.1.1", numericNotice.orElseThrow().availableVersion());
        assertTrue(qualifiedNotice.isPresent());
        assertEquals("0.1.0-rc", qualifiedNotice.orElseThrow().availableVersion());
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
                      "archiveUrl": "https://dist.zolt.sh/artifacts/%s/%s/zolt-%s-linux-x64.tar.gz",
                      "checksumUrl": "https://dist.zolt.sh/artifacts/%s/%s/zolt-%s-linux-x64.tar.gz.sha256",
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

    private static void writeCache(
            Path stateDirectory,
            URI channelUri,
            String target,
            String channel,
            String latestVersion,
            String lastCheckedAt) throws IOException {
        Properties properties = new Properties();
        properties.setProperty("lastCheckedAt", lastCheckedAt);
        properties.setProperty("channelUri", channelUri.toString());
        properties.setProperty("target", target);
        properties.setProperty("channel", channel);
        properties.setProperty("latestVersion", latestVersion);
        Files.createDirectories(stateDirectory);
        try (var output = Files.newOutputStream(stateDirectory.resolve("update-check.properties"))) {
            properties.store(output, "test cache");
        }
    }

    private record InstalledFixture(Path installRoot, Path binLink) {
    }
}
