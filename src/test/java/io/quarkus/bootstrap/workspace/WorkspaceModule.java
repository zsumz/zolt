package io.quarkus.bootstrap.workspace;

import java.nio.file.Path;
import java.util.Collection;

public interface WorkspaceModule {
    interface Mutable extends WorkspaceModule {
        Mutable setBuildFile(Path buildFile);

        Mutable addArtifactSources(ArtifactSources sources);

        Mutable setAdditionalTestClasspathElements(Collection<String> elements);
    }
}
