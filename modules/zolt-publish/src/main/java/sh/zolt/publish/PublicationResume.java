package sh.zolt.publish;

import java.util.Map;

/** Exact hashes recorded by an interrupted publication and available for byte-identical reuse. */
public record PublicationResume(Map<String, String> recordedHashes) {
    public PublicationResume {
        recordedHashes = recordedHashes == null ? Map.of() : Map.copyOf(recordedHashes);
    }

    public static PublicationResume none() {
        return new PublicationResume(Map.of());
    }
}
