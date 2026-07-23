package sh.zolt.sbom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

final class SbomSerialNumberTest {
    @Test
    void derivesKnownVersion5UrnFromSeed() {
        // Pinned against Python's uuid.uuid5(namespace, seed) — an independent RFC 4122 v5 reference.
        assertEquals(
                "urn:uuid:d74fef8c-ed6a-5cc0-a022-2f94c4c91089",
                SbomSerialNumber.serialNumber("sha256:demo-lock-fingerprint"));
    }

    @Test
    void isStableForIdenticalSeeds() {
        assertEquals(
                SbomSerialNumber.serialNumber("sha256:demo-lock-fingerprint"),
                SbomSerialNumber.serialNumber("sha256:demo-lock-fingerprint"));
    }

    @Test
    void isSensitiveToSeedChanges() {
        assertNotEquals(
                SbomSerialNumber.serialNumber("sha256:demo-lock-fingerprint"),
                SbomSerialNumber.serialNumber("sha256:other-lock-fingerprint"));
    }

    @Test
    void fallbackSeedJoinsRootCoordinateAndSortedPurls() {
        String seed = SbomSerialNumber.fallbackSeed(
                "com.example:demo:0.1.0",
                List.of(
                        "pkg:maven/org.example/lib-a@1.0.0?type=jar",
                        "pkg:maven/org.example/lib-b@2.0.0?type=jar"));

        assertEquals(
                "com.example:demo:0.1.0\n"
                        + "pkg:maven/org.example/lib-a@1.0.0?type=jar\n"
                        + "pkg:maven/org.example/lib-b@2.0.0?type=jar",
                seed);
        assertEquals("urn:uuid:17e3598b-f1d1-51eb-af5c-9d8740a5093a", SbomSerialNumber.serialNumber(seed));
    }
}
