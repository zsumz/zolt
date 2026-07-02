package sh.zolt.release.channel;

import sh.zolt.release.ReleaseTarget;
import java.util.Optional;

public record ReleaseChannelArtifact(
        ReleaseTarget target,
        String archive,
        String archiveUrl,
        Optional<String> checksumUrl,
        Optional<String> sha256,
        String format,
        String binaryName,
        Optional<Signature> signature) {
    public ReleaseChannelArtifact {
        checksumUrl = checksumUrl == null ? Optional.empty() : checksumUrl;
        sha256 = sha256 == null ? Optional.empty() : sha256;
        signature = signature == null ? Optional.empty() : signature;
    }

    public record Signature(String kind, String url) {
    }
}
