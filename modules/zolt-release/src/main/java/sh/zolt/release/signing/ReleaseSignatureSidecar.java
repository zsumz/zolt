package sh.zolt.release.signing;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

record ReleaseSignatureSidecar(String version, String keyId, byte[] signature) {
    static final String FORMAT_VERSION = "zolt-ed25519-v1";

    static ReleaseSignatureSidecar parse(String text) {
        if (text == null || text.isBlank()) {
            throw new ReleaseSignatureException("Release signature sidecar is empty.");
        }
        Map<String, String> fields = fields(text);
        String version = required(fields, "version");
        String keyId = required(fields, "keyId");
        String signature = required(fields, "signature");
        if (!FORMAT_VERSION.equals(version)) {
            throw new ReleaseSignatureException(
                    "Release signature sidecar has unsupported version `" + version + "`.");
        }
        if (keyId.isBlank()) {
            throw new ReleaseSignatureException("Release signature sidecar keyId is empty.");
        }
        try {
            return new ReleaseSignatureSidecar(version, keyId, Base64.getDecoder().decode(signature));
        } catch (IllegalArgumentException exception) {
            throw new ReleaseSignatureException("Release signature sidecar signature is not valid Base64.", exception);
        }
    }

    private static Map<String, String> fields(String text) {
        Map<String, String> fields = new HashMap<>();
        for (String line : text.lines().map(String::strip).toList()) {
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            int separator = line.indexOf(':');
            if (separator < 1) {
                throw new ReleaseSignatureException("Release signature sidecar contains malformed line `" + line + "`.");
            }
            String field = line.substring(0, separator).strip();
            String value = line.substring(separator + 1).strip();
            if (fields.put(field, value) != null) {
                throw new ReleaseSignatureException(
                        "Release signature sidecar repeats field `" + field + "`.");
            }
        }
        return fields;
    }

    private static String required(Map<String, String> fields, String field) {
        String value = fields.get(field);
        if (value == null || value.isBlank()) {
            throw new ReleaseSignatureException("Release signature sidecar is missing `" + field + "`.");
        }
        return value;
    }
}
