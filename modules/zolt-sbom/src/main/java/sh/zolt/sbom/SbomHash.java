package sh.zolt.sbom;

/** A CycloneDX {@code hashes[]} entry: an algorithm label and its hex content. */
public record SbomHash(String alg, String content) {
    public SbomHash {
        if (alg == null || alg.isBlank()) {
            throw new IllegalArgumentException("SbomHash alg must not be blank.");
        }
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("SbomHash content must not be blank.");
        }
    }
}
