package com.zolt.arch;

import java.nio.file.Files;
import java.nio.file.Path;

final class RepositoryPaths {
    private RepositoryPaths() {
    }

    static Path root() {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            if (Files.isRegularFile(current.resolve("zolt-workspace.toml"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Could not locate repository root from current working directory.");
    }

    static Path appRoot() {
        return root().resolve("apps/zolt");
    }
}
