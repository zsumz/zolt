package sh.zolt.release.signing;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class ReleaseSignatureVerifier {
    private final Map<String, ReleaseSigningKey> trustedKeys;

    public ReleaseSignatureVerifier(List<ReleaseSigningKey> trustedKeys) {
        this.trustedKeys = trustedKeys.stream()
                .collect(Collectors.toUnmodifiableMap(ReleaseSigningKey::keyId, Function.identity()));
    }

    public static ReleaseSignatureVerifier bundled() {
        return new ReleaseSignatureVerifier(ReleaseTrustedKeys.bundled());
    }

    public void verify(byte[] payload, String sidecarText) {
        ReleaseSignatureSidecar sidecar = ReleaseSignatureSidecar.parse(sidecarText);
        ReleaseSigningKey key = trustedKeys.get(sidecar.keyId());
        if (key == null) {
            throw new ReleaseSignatureException(
                    "Release signature uses untrusted key `" + sidecar.keyId() + "`.");
        }
        if (!"Ed25519".equals(key.algorithm())) {
            throw new ReleaseSignatureException(
                    "Release signature key `" + key.keyId() + "` uses unsupported algorithm `" + key.algorithm() + "`.");
        }
        if (!isValidSignature(payload, sidecar.signature(), key)) {
            throw new ReleaseSignatureException(
                    "Release signature is invalid for key `" + key.keyId() + "`.");
        }
    }

    private static boolean isValidSignature(byte[] payload, byte[] signatureBytes, ReleaseSigningKey key) {
        try {
            KeyFactory keyFactory = KeyFactory.getInstance("Ed25519");
            byte[] publicKeyBytes = Base64.getDecoder().decode(key.x509PublicKeyBase64());
            var publicKey = keyFactory.generatePublic(new X509EncodedKeySpec(publicKeyBytes));
            Signature signature = Signature.getInstance("Ed25519");
            signature.initVerify(publicKey);
            signature.update(payload);
            return signature.verify(signatureBytes);
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new ReleaseSignatureException(
                    "Could not verify release signature for key `" + key.keyId() + "`.",
                    exception);
        }
    }
}
