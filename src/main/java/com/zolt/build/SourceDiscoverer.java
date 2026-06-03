package com.zolt.build;

import com.zolt.project.BuildSettings;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

public final class SourceDiscoverer {
    private static final Set<String> OUTPUT_DIRECTORY_NAMES = Set.of("target", "build");

    public SourceDiscoveryResult discover(Path projectDirectory, BuildSettings settings) {
        Path output = projectDirectory.resolve(settings.output()).normalize();
        Path testOutput = projectDirectory.resolve(settings.testOutput()).normalize();
        return new SourceDiscoveryResult(
                discoverSources(projectDirectory.resolve(settings.source()).normalize(), output, testOutput),
                discoverSources(projectDirectory.resolve(settings.test()).normalize(), output, testOutput));
    }

    private static List<Path> discoverSources(Path root, Path output, Path testOutput) {
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        try (Stream<Path> paths = Files.walk(root)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".java"))
                    .map(Path::normalize)
                    .filter(path -> !path.startsWith(output))
                    .filter(path -> !path.startsWith(testOutput))
                    .filter(path -> !startsWithOutputDirectorySegment(root.relativize(path)))
                    .sorted()
                    .toList();
        } catch (IOException exception) {
            throw new SourceDiscoveryException(
                    "Could not discover Java sources under "
                            + root
                            + ". Check that the directory is readable.",
                    exception);
        }
    }

    private static boolean startsWithOutputDirectorySegment(Path relativePath) {
        if (relativePath.getNameCount() == 0) {
            return false;
        }
        return OUTPUT_DIRECTORY_NAMES.contains(relativePath.getName(0).toString());
    }
}
