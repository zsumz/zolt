package io.quarkus.bootstrap.workspace;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class FakeWorkspaceModule implements WorkspaceModule.Mutable {
    private final WorkspaceModuleId moduleId;
    private final Path moduleDirectory;
    private final Path buildDirectory;
    private final List<FakeArtifactSources> artifactSources = new ArrayList<>();
    private Path buildFile;
    private List<String> additionalTestClasspathElements = List.of();

    public FakeWorkspaceModule(
            WorkspaceModuleId moduleId,
            Path moduleDirectory,
            Path buildDirectory) {
        this.moduleId = moduleId;
        this.moduleDirectory = moduleDirectory;
        this.buildDirectory = buildDirectory;
    }

    @Override
    public WorkspaceModule.Mutable setBuildFile(Path buildFile) {
        this.buildFile = buildFile;
        return this;
    }

    @Override
    public WorkspaceModule.Mutable addArtifactSources(ArtifactSources sources) {
        artifactSources.add((FakeArtifactSources) sources);
        return this;
    }

    @Override
    public WorkspaceModule.Mutable setAdditionalTestClasspathElements(Collection<String> elements) {
        additionalTestClasspathElements = List.copyOf(elements);
        return this;
    }

    public WorkspaceModuleId moduleId() {
        return moduleId;
    }

    public Path moduleDirectory() {
        return moduleDirectory;
    }

    public Path buildDirectory() {
        return buildDirectory;
    }

    public Path buildFile() {
        return buildFile;
    }

    public List<FakeArtifactSources> artifactSources() {
        return List.copyOf(artifactSources);
    }

    public List<String> additionalTestClasspathElements() {
        return additionalTestClasspathElements;
    }
}
