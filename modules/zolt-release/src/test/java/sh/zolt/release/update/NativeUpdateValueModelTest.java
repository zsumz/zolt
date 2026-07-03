package sh.zolt.release.update;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import sh.zolt.release.ReleaseTarget;
import java.net.URI;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class NativeUpdateValueModelTest {
    @Test
    void noticeMessageNamesChannelVersionsAndNativeArchiveAction() {
        NativeUpdateNotice notice = new NativeUpdateNotice(
                "zap",
                ReleaseTarget.LINUX_X64,
                "0.1.0-zap.20260702.0123456",
                "0.1.0-zap.20260703.abcdef0",
                false);

        assertFalse(notice.cached());
        assertEquals(ReleaseTarget.LINUX_X64, notice.target());
        assertEquals(
                "A newer Zolt is available on zap: 0.1.0-zap.20260702.0123456 -> 0.1.0-zap.20260703.abcdef0. Download and verify the latest native archive for this channel.",
                notice.message());
    }

    @Test
    void updateRequestAndResultCarryInstallerPathsAndVersions() {
        NativeUpdateRequest request = new NativeUpdateRequest(
                Path.of("/opt/zolt"),
                Path.of("/opt/zolt/bin/zolt"),
                URI.create("https://dist.zolt.sh/channels/stable.json"),
                ReleaseTarget.MACOS_ARM64,
                Path.of("/tmp/zolt-update"));
        NativeUpdateResult result = new NativeUpdateResult(
                "stable",
                request.target(),
                "0.1.0",
                "0.1.1",
                true,
                request.currentExecutable());

        assertEquals(Path.of("/opt/zolt"), request.installRoot());
        assertEquals(URI.create("https://dist.zolt.sh/channels/stable.json"), request.channelUri());
        assertEquals("stable", result.channel());
        assertEquals("0.1.0", result.previousVersion());
        assertEquals("0.1.1", result.availableVersion());
        assertEquals(Path.of("/opt/zolt/bin/zolt"), result.executable());
    }
}
