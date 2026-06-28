package com.zolt.release;

import java.util.List;

public record ReleaseChannelManifest(
        int schemaVersion,
        String channel,
        String version,
        String commit,
        String createdAt,
        List<ReleaseChannelArtifact> artifacts) {
    public ReleaseChannelManifest {
        artifacts = List.copyOf(artifacts);
    }

    public ReleaseChannelArtifact artifactFor(ReleaseTarget target) {
        return artifacts.stream()
                .filter(artifact -> artifact.target() == target)
                .findFirst()
                .orElseThrow(() -> new ReleaseChannelManifestException(
                        "Release channel `"
                                + channel
                                + "` does not include native archive target `"
                                + target.id()
                                + "`. Supported targets in this manifest: "
                                + artifacts.stream()
                                        .map(artifact -> artifact.target().id())
                                        .sorted()
                                        .toList()
                                + "."));
    }
}
