package sh.zolt.toolchain.catalog;

import java.net.URI;
import java.util.Optional;

public record JavaToolchainArtifact(
        URI uri,
        JavaToolchainArchiveFormat format,
        Optional<String> sha256,
        boolean stripTopLevelDirectory) {
    public JavaToolchainArtifact {
        if (uri == null) {
            throw new IllegalArgumentException("Java toolchain artifact URI is required.");
        }
        if (format == null) {
            throw new IllegalArgumentException("Java toolchain artifact archive format is required.");
        }
        sha256 = sha256 == null ? Optional.empty() : sha256.map(String::strip).filter(value -> !value.isBlank());
    }
}
