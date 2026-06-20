package io.quarkus.bootstrap.workspace;

public interface ArtifactSources {
    static ArtifactSources main(SourceDir sourceDir, SourceDir resourceDir) {
        return new FakeArtifactSources("", sourceDir, resourceDir);
    }

    static ArtifactSources test(SourceDir sourceDir, SourceDir resourceDir) {
        return new FakeArtifactSources("tests", sourceDir, resourceDir);
    }
}
