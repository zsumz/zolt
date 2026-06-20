package com.zolt.toml;

import java.nio.file.Files;
import java.nio.file.Path;

final class ZoltTomlTestPaths {
    private ZoltTomlTestPaths() {
    }

    static Path fixture(String relativePath) {
        Path current = Path.of("").toAbsolutePath().normalize();
        for (Path directory = current; directory != null; directory = directory.getParent()) {
            Path candidate = directory.resolve(relativePath).normalize();
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        return current.resolve(relativePath).normalize();
    }
}
