package com.zolt.arch;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.List;
import java.util.stream.Stream;

final class ArchitectureBudgetSupport {
    private ArchitectureBudgetSupport() {}

    static List<Path> sourceRoots(Path rootPattern) throws IOException {
        Path resolvedPattern = resolveRootPattern(rootPattern);
        if (!containsWildcard(resolvedPattern)) {
            return Files.isDirectory(resolvedPattern) ? List.of(resolvedPattern) : List.of();
        }
        Path base = wildcardBase(resolvedPattern);
        if (!Files.isDirectory(base)) {
            return List.of();
        }
        PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + resolvedPattern);
        try (Stream<Path> paths = Files.walk(base)) {
            return paths.filter(Files::isDirectory)
                    .map(Path::normalize)
                    .filter(matcher::matches)
                    .sorted()
                    .toList();
        }
    }

    private static Path resolveRootPattern(Path rootPattern) {
        if (rootPattern.isAbsolute()) {
            return rootPattern.normalize();
        }
        return RepositoryPaths.root().resolve(rootPattern).normalize();
    }

    private static boolean containsWildcard(Path path) {
        return path.toString().contains("*");
    }

    private static Path wildcardBase(Path pattern) {
        Path base = pattern.getRoot();
        for (Path part : pattern) {
            if (containsWildcard(part)) {
                break;
            }
            base = base == null ? part : base.resolve(part);
        }
        return base == null ? Path.of(".") : base;
    }
}
