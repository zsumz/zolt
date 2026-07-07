package sh.zolt.release.signing;

public record ReleaseSigningKey(String keyId, String algorithm, String x509PublicKeyBase64) {
}
