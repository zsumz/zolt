package sh.zolt.build.fingerprint;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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
                .filter(path -> !isPackageInfo(path) || Files.isRegularFile(path))
                .sorted()
                .map(path -> relative(projectRoot, path))
                .toList();
    }

    List<Path> missing(Path projectRoot, String fingerprint) {
        List<Path> missing = new ArrayList<>();
        boolean expectedClassesSection = false;
        for (String line : fingerprint.lines().toList()) {
            if (line.startsWith("[") && line.endsWith("]")) {
                expectedClassesSection = "[expectedClasses]".equals(line);
                continue;
            }
            if (expectedClassesSection && !line.isBlank()) {
                Path recorded = Path.of(line);
                Path expectedClass = recorded.isAbsolute()
                        ? recorded.normalize()
                        : projectRoot.resolve(recorded).normalize();
                if (!Files.isRegularFile(expectedClass)) {
                    missing.add(expectedClass);
                }
            }
        }
        return List.copyOf(missing);
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

    private static boolean isPackageInfo(Path classFile) {
        Path fileName = classFile.getFileName();
        return fileName != null && "package-info.class".equals(fileName.toString());
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
