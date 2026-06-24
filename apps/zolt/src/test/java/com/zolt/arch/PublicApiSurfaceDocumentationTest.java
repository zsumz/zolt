package com.zolt.arch;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;

final class PublicApiSurfaceDocumentationTest {
    @Test
    void publicApiSurfaceDocumentNamesOwnedSurfacesAndChangeRules() throws IOException {
        String document = Files.readString(RepositoryPaths.root().resolve("docs/public-api-surface.md"));

        assertTrue(document.contains("Project and dependency model"));
        assertTrue(document.contains("Framework capability API"));
        assertTrue(document.contains("IDE model contract"));
        assertTrue(document.contains("PublicApiSurfaceTest"));
        assertTrue(document.contains("New public types require an intentional allowlist update"));
        assertTrue(document.contains("Deprecated public APIs should name a removal target"));
    }

    @Test
    void docsIndexLinksPublicApiSurface() throws IOException {
        String docsIndex = Files.readString(RepositoryPaths.root().resolve("docs/README.md"));

        assertTrue(docsIndex.contains("`public-api-surface.md`"));
    }
}
