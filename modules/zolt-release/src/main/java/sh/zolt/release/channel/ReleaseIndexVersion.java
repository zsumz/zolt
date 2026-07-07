package sh.zolt.release.channel;

import sh.zolt.release.ReleaseTarget;
import java.util.List;

public record ReleaseIndexVersion(
        String version,
        String commit,
        String createdAt,
        List<ReleaseChannelArtifact> artifacts) {
    public ReleaseIndexVersion {
        artifacts = List.copyOf(artifacts);
    }

    public ReleaseChannelArtifact artifactFor(ReleaseTarget target) {
        return artifacts.stream()
                .filter(artifact -> artifact.target() == target)
                .findFirst()
                .orElseThrow(() -> new ReleaseChannelManifestException(
                        "Release index version `"
                                + version
                                + "` does not include native archive target `"
                                + target.id()
                                + "`. Supported targets in this version: "
                                + artifacts.stream()
                                        .map(artifact -> artifact.target().id())
                                        .sorted()
                                        .toList()
                                + "."));
    }
}
