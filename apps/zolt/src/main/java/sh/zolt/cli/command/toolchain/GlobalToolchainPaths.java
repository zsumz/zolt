package sh.zolt.cli.command.toolchain;

import java.nio.file.Path;

final class GlobalToolchainPaths {
    private GlobalToolchainPaths() {
    }

    static Path defaultConfigPath() {
        return Path.of(System.getProperty("user.home"), ".zolt", "config.toml").toAbsolutePath().normalize();
    }

    static Path lockfile(Path configPath) {
        Path normalized = configPath.toAbsolutePath().normalize();
        Path parent = normalized.getParent();
        return (parent == null ? Path.of("global-toolchains.lock") : parent.resolve("global-toolchains.lock"))
                .toAbsolutePath()
                .normalize();
    }
}
