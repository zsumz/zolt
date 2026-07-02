package sh.zolt.explain;

import java.io.IOException;
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
            if (isWorkspaceRoot(current)) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Could not locate repository root from " + Path.of("").toAbsolutePath());
    }

    private static boolean isWorkspaceRoot(Path current) {
        if (Files.isRegularFile(current.resolve("zolt-workspace.toml"))) {
            return true;
        }
        Path rootConfig = current.resolve("zolt.toml");
        if (!Files.isRegularFile(rootConfig)) {
            return false;
        }
        try {
            return Files.readString(rootConfig).lines().map(String::trim).anyMatch("[workspace]"::equals);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read root config at " + rootConfig + ".", exception);
        }
    }
}
