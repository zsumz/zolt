package com.zolt.build.fingerprint;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

final class BuildFingerprintExpectedClasses {
    List<String> entries(
            Path projectRoot,
            List<String> sourceRoots,
            List<Path> sources,
            Path outputDirectory) {
        return files(projectRoot, sourceRoots, sources, outputDirectory).stream()
                .sorted()
                .map(path -> relative(projectRoot, path))
                .toList();
    }

    boolean present(
            Path projectRoot,
            List<String> sourceRoots,
            List<Path> sources,
            Path outputDirectory) {
        for (Path expectedClass : files(projectRoot, sourceRoots, sources, outputDirectory)) {
            if (!java.nio.file.Files.isRegularFile(expectedClass)) {
                return false;
            }
        }
        return true;
    }

    List<Path> files(
            Path projectRoot,
            List<String> sourceRoots,
            List<Path> sources,
            Path outputDirectory) {
        List<Path> roots = sourceRoots.stream()
                .map(root -> projectRoot.resolve(root).normalize())
                .toList();
        return sources.stream()
                .map(path -> path.toAbsolutePath().normalize())
                .map(path -> classFile(sourceRootFor(roots, path), path, outputDirectory))
                .flatMap(Optional::stream)
                .sorted()
                .toList();
    }

    private static Optional<Path> classFile(Optional<Path> sourceRoot, Path source, Path outputDirectory) {
        if (sourceRoot.isEmpty()) {
            return Optional.empty();
        }
        Path relative = sourceRoot.orElseThrow().relativize(source);
        String fileName = relative.getFileName().toString();
        String extension;
        if (fileName.endsWith(".java")) {
            extension = ".java";
        } else if (fileName.endsWith(".groovy")) {
            extension = ".groovy";
        } else {
            return Optional.empty();
        }
        Path classRelative = relative.getParent() == null
                ? Path.of(fileName.substring(0, fileName.length() - extension.length()) + ".class")
                : relative.getParent().resolve(fileName.substring(0, fileName.length() - extension.length()) + ".class");
        return Optional.of(outputDirectory.resolve(classRelative).normalize());
    }

    private static Optional<Path> sourceRootFor(List<Path> sourceRoots, Path source) {
        return sourceRoots.stream()
                .filter(source::startsWith)
                .max(Comparator.comparingInt(Path::getNameCount));
    }

    private static String relative(Path projectRoot, Path path) {
        Path normalized = path.toAbsolutePath().normalize();
        if (normalized.startsWith(projectRoot)) {
            return projectRoot.relativize(normalized).toString().replace('\\', '/');
        }
        return normalized.toString().replace('\\', '/');
    }
}
