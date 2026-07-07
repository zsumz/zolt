package sh.zolt.release.channel;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class ReleaseIndexManifestService {
    private static final int BETA_SCHEMA_VERSION = 1;

    public ReleaseIndexManifest merge(
            ReleaseChannelManifest channelManifest,
            Optional<ReleaseIndexManifest> previousIndex,
            int limit) {
        if (limit < 1) {
            throw new ReleaseChannelManifestException("Release index limit must be at least 1.");
        }
        previousIndex.ifPresent(previous -> {
            if (!previous.channel().equals(channelManifest.channel())) {
                throw new ReleaseChannelManifestException(
                        "Release index channel `"
                                + previous.channel()
                                + "` does not match release channel `"
                                + channelManifest.channel()
                                + "`.");
            }
        });

        List<ReleaseIndexVersion> versions = new ArrayList<>();
        versions.add(new ReleaseIndexVersion(
                channelManifest.version(),
                channelManifest.commit(),
                channelManifest.createdAt(),
                channelManifest.artifacts()));
        previousIndex.ifPresent(previous -> {
            for (ReleaseIndexVersion version : previous.versions()) {
                if (versions.size() >= limit) {
                    break;
                }
                if (!version.version().equals(channelManifest.version())) {
                    versions.add(version);
                }
            }
        });
        return new ReleaseIndexManifest(
                BETA_SCHEMA_VERSION,
                channelManifest.channel(),
                channelManifest.createdAt(),
                versions);
    }
}
