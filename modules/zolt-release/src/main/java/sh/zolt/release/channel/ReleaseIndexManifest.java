package sh.zolt.release.channel;

import java.util.List;

public record ReleaseIndexManifest(
        int schemaVersion,
        String channel,
        String updatedAt,
        List<ReleaseIndexVersion> versions) {
    public ReleaseIndexManifest {
        versions = List.copyOf(versions);
    }
}
