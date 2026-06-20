package com.zolt.explain;

import java.nio.file.Files;
import java.nio.file.Path;

final class MigrationExplainTestPaths {
    private MigrationExplainTestPaths() {}

    static Path fixtureRoot() {
        return repositoryRoot().resolve("examples/migration-explain").normalize();
    }

    private static Path repositoryRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            if (Files.isRegularFile(current.resolve("zolt-workspace.toml"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Could not locate repository root from " + Path.of("").toAbsolutePath());
    }
}
