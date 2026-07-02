package sh.zolt.explain.emit;

/** One zolt.toml document rendered from a draft emit model, tagged with its output path. */
public record DraftZoltTomlDocument(String relativePath, String contents) {
    public DraftZoltTomlDocument {
        if (relativePath == null || relativePath.isBlank()) {
            throw new IllegalArgumentException("A draft zolt.toml document must have a non-blank relative path.");
        }
        if (contents == null) {
            throw new IllegalArgumentException("A draft zolt.toml document must have contents.");
        }
    }
}
