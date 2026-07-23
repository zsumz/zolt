package sh.zolt.build.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

final class BuildCacheKeyTest {
    @Test
    void isStableForIdenticalInputs() {
        assertEquals(
                BuildCacheKey.of(BuildCacheScope.MAIN, "inputsha", "temurin-21"),
                BuildCacheKey.of(BuildCacheScope.MAIN, "inputsha", "temurin-21"));
    }

    @Test
    void differsWhenInputsFingerprintDiffers() {
        assertNotEquals(
                BuildCacheKey.of(BuildCacheScope.MAIN, "inputsha-a", "21").hash(),
                BuildCacheKey.of(BuildCacheScope.MAIN, "inputsha-b", "21").hash());
    }

    @Test
    void differsWhenScopeDiffers() {
        assertNotEquals(
                BuildCacheKey.of(BuildCacheScope.MAIN, "inputsha", "21").hash(),
                BuildCacheKey.of(BuildCacheScope.TEST, "inputsha", "21").hash());
    }

    @Test
    void differsWhenJdkIdentityDiffers() {
        assertNotEquals(
                BuildCacheKey.of(BuildCacheScope.MAIN, "inputsha", "21").hash(),
                BuildCacheKey.of(BuildCacheScope.MAIN, "inputsha", "23").hash());
    }

    @Test
    void retainsScope() {
        assertEquals(BuildCacheScope.TEST, BuildCacheKey.of(BuildCacheScope.TEST, "x", "21").scope());
    }
}
