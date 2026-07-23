package sh.zolt.sbom;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Builds Package URLs (purls) for Maven components:
 * {@code pkg:maven/<group>/<name>@<version>?classifier=<c>&type=<ext>}.
 *
 * <p>Each path/qualifier value is percent-encoded (RFC 3986 unreserved set kept). Qualifier keys are
 * emitted in canonical sorted order (so {@code classifier} precedes {@code type}), which keeps the
 * purl in purl-spec canonical form regardless of how many qualifiers a component carries.
 */
public final class PurlWriter {
    private PurlWriter() {
    }

    /**
     * @param group the Maven groupId
     * @param name the Maven artifactId
     * @param version the resolved version
     * @param type the artifact extension (purl {@code type} qualifier), for example {@code jar}
     * @param classifier the artifact classifier, when the artifact carries one
     */
    public static String purl(
            String group,
            String name,
            String version,
            String type,
            Optional<String> classifier) {
        StringBuilder purl = new StringBuilder("pkg:maven/");
        purl.append(encode(group)).append('/').append(encode(name)).append('@').append(encode(version));

        TreeMap<String, String> qualifiers = new TreeMap<>();
        classifier
                .filter(value -> !value.isBlank())
                .ifPresent(value -> qualifiers.put("classifier", value));
        if (type != null && !type.isBlank()) {
            qualifiers.put("type", type);
        }
        if (!qualifiers.isEmpty()) {
            purl.append('?');
            boolean first = true;
            for (var qualifier : qualifiers.entrySet()) {
                if (!first) {
                    purl.append('&');
                }
                purl.append(qualifier.getKey()).append('=').append(encode(qualifier.getValue()));
                first = false;
            }
        }
        return purl.toString();
    }

    private static String encode(String value) {
        StringBuilder encoded = new StringBuilder(value.length());
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        for (byte raw : bytes) {
            int unsigned = raw & 0xFF;
            if (isUnreserved(unsigned)) {
                encoded.append((char) unsigned);
            } else {
                encoded.append('%').append(hex(unsigned >> 4)).append(hex(unsigned & 0x0F));
            }
        }
        return encoded.toString();
    }

    private static boolean isUnreserved(int character) {
        return (character >= 'A' && character <= 'Z')
                || (character >= 'a' && character <= 'z')
                || (character >= '0' && character <= '9')
                || character == '-'
                || character == '.'
                || character == '_'
                || character == '~';
    }

    private static char hex(int nibble) {
        return (char) (nibble < 10 ? '0' + nibble : 'A' + (nibble - 10));
    }
}
