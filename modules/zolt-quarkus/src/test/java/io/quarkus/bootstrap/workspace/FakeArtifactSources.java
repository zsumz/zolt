package io.quarkus.bootstrap.workspace;

public record FakeArtifactSources(
        String classifier,
        SourceDir sourceDir,
        SourceDir resourceDir) implements ArtifactSources {
}
