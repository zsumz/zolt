package sh.zolt.sbom;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

/**
 * Derives the CycloneDX {@code serialNumber} ({@code urn:uuid:<uuid>}) as a name-based RFC 4122
 * version 5 UUID over a deterministic seed. Identical inputs always yield an identical serial; the
 * serial is never random. The seed is the lockfile's {@code projectResolutionFingerprint} when
 * present, and otherwise a fallback derived from the root coordinate plus the sorted component purls.
 *
 * <p>The v5 UUID is computed by hand (SHA-1 over {@code namespace-bytes ‖ name-bytes}, with the
 * version and variant bits stamped) so no assumptions about JDK helpers leak into the wire bytes.
 */
public final class SbomSerialNumber {
    /**
     * Fixed namespace UUID for Zolt SBOM serials. Arbitrary but constant: changing it would change
     * every serial, so it is pinned here forever.
     */
    static final UUID NAMESPACE = UUID.fromString("5f3e9b2a-8c47-4d1e-b6a9-2f0c8e7d1a34");

    private SbomSerialNumber() {
    }

    /** The {@code urn:uuid:...} serial number for {@code seed}. */
    public static String serialNumber(String seed) {
        return "urn:uuid:" + version5(NAMESPACE, seed);
    }

    /** Builds the fallback seed from the root coordinate and already-sorted component purls. */
    public static String fallbackSeed(String rootCoordinate, Iterable<String> sortedPurls) {
        StringBuilder seed = new StringBuilder(rootCoordinate);
        for (String purl : sortedPurls) {
            seed.append('\n').append(purl);
        }
        return seed.toString();
    }

    static UUID version5(UUID namespace, String name) {
        byte[] namespaceBytes = toBytes(namespace);
        byte[] nameBytes = name.getBytes(StandardCharsets.UTF_8);
        byte[] input = new byte[namespaceBytes.length + nameBytes.length];
        System.arraycopy(namespaceBytes, 0, input, 0, namespaceBytes.length);
        System.arraycopy(nameBytes, 0, input, namespaceBytes.length, nameBytes.length);

        byte[] hash = sha1(input);
        byte[] uuidBytes = new byte[16];
        System.arraycopy(hash, 0, uuidBytes, 0, 16);
        uuidBytes[6] = (byte) ((uuidBytes[6] & 0x0F) | 0x50); // version 5
        uuidBytes[8] = (byte) ((uuidBytes[8] & 0x3F) | 0x80); // RFC 4122 variant
        return fromBytes(uuidBytes);
    }

    private static byte[] toBytes(UUID uuid) {
        byte[] bytes = new byte[16];
        long most = uuid.getMostSignificantBits();
        long least = uuid.getLeastSignificantBits();
        for (int index = 0; index < 8; index++) {
            bytes[index] = (byte) (most >>> (8 * (7 - index)));
            bytes[8 + index] = (byte) (least >>> (8 * (7 - index)));
        }
        return bytes;
    }

    private static UUID fromBytes(byte[] bytes) {
        long most = 0;
        long least = 0;
        for (int index = 0; index < 8; index++) {
            most = (most << 8) | (bytes[index] & 0xFFL);
            least = (least << 8) | (bytes[8 + index] & 0xFFL);
        }
        return new UUID(most, least);
    }

    private static byte[] sha1(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-1").digest(input);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-1 is required for SBOM serial numbers.", exception);
        }
    }
}
