package sh.zolt.dependency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class VersionStabilityTest {

    @Test
    void plainNumbersAreRelease() {
        assertEquals(VersionStability.RELEASE, VersionStability.of("1.2.3"));
        assertEquals(VersionStability.RELEASE, VersionStability.of("1"));
        assertEquals(VersionStability.RELEASE, VersionStability.of("1.0.0"));
    }

    @Test
    void knownReleaseWordsAreRelease() {
        assertEquals(VersionStability.RELEASE, VersionStability.of("1.0.0.Final"));
        assertEquals(VersionStability.RELEASE, VersionStability.of("1.0-GA"));
        assertEquals(VersionStability.RELEASE, VersionStability.of("1.0-release"));
        assertEquals(VersionStability.RELEASE, VersionStability.of("1.2.3-sp1"));
    }

    @Test
    void unknownFlavorQualifiersAreRelease() {
        assertEquals(VersionStability.RELEASE, VersionStability.of("33.4.8-jre"));
        assertEquals(VersionStability.RELEASE, VersionStability.of("33.4.8-android"));
        assertEquals(VersionStability.RELEASE, VersionStability.of("2.9.0-jakarta"));
    }

    @Test
    void calendarVersionsAreRelease() {
        assertEquals(VersionStability.RELEASE, VersionStability.of("2023.10.5"));
        assertEquals(VersionStability.RELEASE, VersionStability.of("20231005"));
    }

    @Test
    void knownNegativeQualifiersArePrerelease() {
        assertEquals(VersionStability.PRERELEASE, VersionStability.of("1.0-alpha"));
        assertEquals(VersionStability.PRERELEASE, VersionStability.of("1.0-a"));
        assertEquals(VersionStability.PRERELEASE, VersionStability.of("1.0-beta"));
        assertEquals(VersionStability.PRERELEASE, VersionStability.of("1.0-b"));
        assertEquals(VersionStability.PRERELEASE, VersionStability.of("1.0-milestone"));
        assertEquals(VersionStability.PRERELEASE, VersionStability.of("1.0-m1"));
        assertEquals(VersionStability.PRERELEASE, VersionStability.of("1.0-rc1"));
        assertEquals(VersionStability.PRERELEASE, VersionStability.of("1.0-cr2"));
    }

    @Test
    void expandedPrereleaseQualifiersArePrerelease() {
        assertEquals(VersionStability.PRERELEASE, VersionStability.of("2.0.0-ea.1"));
        assertEquals(VersionStability.PRERELEASE, VersionStability.of("1.5.0-preview"));
        assertEquals(VersionStability.PRERELEASE, VersionStability.of("3.0.0-dev"));
        assertEquals(VersionStability.PRERELEASE, VersionStability.of("0.9.0-nightly"));
        assertEquals(VersionStability.PRERELEASE, VersionStability.of("1.0.0-canary.3"));
        assertEquals(VersionStability.PRERELEASE, VersionStability.of("1.2.0-pre"));
        assertEquals(VersionStability.PRERELEASE, VersionStability.of("1.0.0-experimental"));
    }

    @Test
    void releaseFlavorsAndIncubatingStayRelease() {
        assertEquals(VersionStability.RELEASE, VersionStability.of("33.4.8-jre"));
        assertEquals(VersionStability.RELEASE, VersionStability.of("4.1.100.Final"));
        assertEquals(VersionStability.RELEASE, VersionStability.of("20231013"));
        assertEquals(VersionStability.RELEASE, VersionStability.of("2.0-android"));
        assertEquals(VersionStability.RELEASE, VersionStability.of("2.9.0-native"));
        // Apache incubator artifacts are published releases; the token stays out of the prerelease set.
        assertEquals(VersionStability.RELEASE, VersionStability.of("1.0.0-incubating"));
    }

    @Test
    void qualifiersFusedToDigitsStillClassify() {
        assertEquals(VersionStability.PRERELEASE, VersionStability.of("1.2.3.rc1"));
        assertEquals(VersionStability.PRERELEASE, VersionStability.of("5.0.0alpha1"));
        assertEquals(VersionStability.RELEASE, VersionStability.of("1.0.0-jre8"));
    }

    @Test
    void snapshotSuffixIsSnapshotRegardlessOfCase() {
        assertEquals(VersionStability.SNAPSHOT, VersionStability.of("1.0.0-SNAPSHOT"));
        assertEquals(VersionStability.SNAPSHOT, VersionStability.of("1.0.0-snapshot"));
    }

    @Test
    void snapshotDominatesPrereleaseTokens() {
        assertEquals(VersionStability.SNAPSHOT, VersionStability.of("1.0-alpha-SNAPSHOT"));
    }

    @Test
    void onlySnapshotIsNotSuggestable() {
        assertTrue(VersionStability.RELEASE.suggestable());
        assertTrue(VersionStability.PRERELEASE.suggestable());
        assertFalse(VersionStability.SNAPSHOT.suggestable());
    }
}
