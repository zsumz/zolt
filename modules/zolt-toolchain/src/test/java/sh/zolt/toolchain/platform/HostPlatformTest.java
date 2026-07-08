package sh.zolt.toolchain.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class HostPlatformTest {
    @Test
    void parsesTargetAliases() {
        assertEquals("macos-aarch64", HostPlatform.parse("darwin-arm64").id());
        assertEquals("linux-x64", HostPlatform.parse("linux-amd64").id());
        assertEquals("windows-x64", HostPlatform.parse("windows-x86_64").id());
    }
}
