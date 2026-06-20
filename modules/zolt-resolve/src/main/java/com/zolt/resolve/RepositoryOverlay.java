package com.zolt.resolve;

import java.nio.file.Path;
import java.util.Objects;

public record RepositoryOverlay(String id, RepositoryOverlayKind kind, Path root) {
    public RepositoryOverlay {
        if (id == null || id.isBlank()) {
            throw new ResolveException("Repository overlay id must not be blank.");
        }
        id = id.trim();
        kind = Objects.requireNonNull(kind, "kind");
        root = Objects.requireNonNull(root, "root").toAbsolutePath().normalize();
    }

    public static RepositoryOverlay mavenLocal(Path root) {
        return new RepositoryOverlay("maven-local", RepositoryOverlayKind.MAVEN_LOCAL, root);
    }

    public String lockfileSource() {
        return "local-overlay:" + id;
    }
}
