package com.zolt.release;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class ReleaseTargetTest {
    @Test
    void currentTargetInfersLinuxArm64FromAarch64() {
        String originalOs = System.getProperty("os.name");
        String originalArch = System.getProperty("os.arch");
        try {
            System.setProperty("os.name", "Linux");
            System.setProperty("os.arch", "aarch64");

            assertEquals(ReleaseTarget.LINUX_ARM64, ReleaseTarget.current());
        } finally {
            restoreSystemProperty("os.name", originalOs);
            restoreSystemProperty("os.arch", originalArch);
        }
    }

    @Test
    void unknownTargetListsSupportedTargets() {
        ReleaseArchiveException exception = org.junit.jupiter.api.Assertions.assertThrows(
                ReleaseArchiveException.class,
                () -> ReleaseTarget.fromId("solaris-sparc"));

        assertTrue(exception.getMessage().contains("Unknown release target `solaris-sparc`"));
        assertTrue(exception.getMessage().contains("macos-arm64"));
        assertTrue(exception.getMessage().contains("linux-arm64"));
        assertTrue(exception.getMessage().contains("windows-x64"));
    }

    private static void restoreSystemProperty(String key, String value) {
        if (value == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, value);
        }
    }
}
