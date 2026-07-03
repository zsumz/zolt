package sh.zolt.release.update;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import sh.zolt.release.ReleaseTarget;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class NativeUpdateNoticeCacheFailureTest extends NativeUpdateNoticeServiceTestCase {
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
    void fallsBackToCachedNoticeWhenRemoteManifestDownloadFails() throws IOException {
        InstalledFixture installed = install("0.1.0");
        URI remoteChannel = URI.create("https://127.0.0.1:1/channels/stable.json");
        Path state = tempDir.resolve("remote-state");
        writeCache(
                state,
                remoteChannel,
                "linux-x64",
                "stable",
                "0.1.2",
                "not-an-instant");

        Optional<NativeUpdateNotice> notice = service.check(request(
                installed,
                remoteChannel,
                now,
                state,
                Duration.ofHours(24)));

        assertTrue(notice.isPresent());
        assertTrue(notice.orElseThrow().cached());
        assertEquals("0.1.2", notice.orElseThrow().availableVersion());
    }

    @Test
    void cacheWriteFailureDoesNotSuppressFreshNotice() throws IOException {
        InstalledFixture installed = install("0.1.0");
        Path channel = writeChannel("stable", "0.1.1");
        Path stateFile = tempDir.resolve("state-file");
        Files.writeString(stateFile, "not a directory");

        Optional<NativeUpdateNotice> notice = service.check(request(
                installed,
                channel.toUri(),
                now,
                stateFile,
                Duration.ZERO));

        assertTrue(notice.isPresent());
        assertFalse(notice.orElseThrow().cached());
        assertEquals("0.1.1", notice.orElseThrow().availableVersion());
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
}
