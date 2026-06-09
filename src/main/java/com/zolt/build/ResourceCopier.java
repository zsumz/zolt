package com.zolt.build;

import com.zolt.project.BuildSettings;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

public final class ResourceCopier {
    private static final Set<String> OUTPUT_DIRECTORY_NAMES = Set.of("target", "build");

    public ResourceCopyResult copyMainResources(Path projectDirectory, BuildSettings settings) {
        return copyResources(
                settings.resourceRoots(),
                projectDirectory.toAbsolutePath().normalize().resolve(settings.output()).normalize(),
                projectDirectory,
                settings);
    }

    public ResourceCopyResult copyTestResources(Path projectDirectory, BuildSettings settings) {
        return copyResources(
                settings.testResourceRoots(),
                projectDirectory.toAbsolutePath().normalize().resolve(settings.testOutput()).normalize(),
                projectDirectory,
                settings);
    }

    private static ResourceCopyResult copyResources(
            List<String> configuredRoots,
            Path outputDirectory,
            Path projectDirectory,
            BuildSettings settings) {
        Path projectRoot = projectDirectory.toAbsolutePath().normalize();
        Path mainOutput = projectRoot.resolve(settings.output()).normalize();
        Path testOutput = projectRoot.resolve(settings.testOutput()).normalize();
        List<Path> copiedResources = new ArrayList<>();
        List<Path> skippedResources = new ArrayList<>();
        Set<Path> targetRelativePaths = new HashSet<>();
        for (String configuredRoot : configuredRoots) {
            Path resourceRoot = resourceRoot(projectDirectory, configuredRoot);
            if (!Files.isDirectory(resourceRoot)) {
                continue;
            }

            try (Stream<Path> paths = Files.walk(resourceRoot)) {
                List<Path> resources = paths
                        .filter(Files::isRegularFile)
                        .map(Path::normalize)
                        .filter(path -> !path.getFileName().toString().endsWith(".java"))
                        .filter(path -> !path.startsWith(mainOutput))
                        .filter(path -> !path.startsWith(testOutput))
                        .filter(path -> !startsWithOutputDirectorySegment(resourceRoot.relativize(path)))
                        .sorted()
                        .toList();

                for (Path resource : resources) {
                    Path relativePath = resourceRoot.relativize(resource).normalize();
                    if (!targetRelativePaths.add(relativePath)) {
                        throw new ResourceCopyException(
                                "Duplicate resource path `"
                                        + relativePath.toString().replace('\\', '/')
                                        + "` from configured resource roots. Remove one copy or choose a distinct output path.");
                    }
                    Path target = outputDirectory.resolve(relativePath).normalize();
                    Files.createDirectories(target.getParent());
                    if (isCurrent(resource, target)) {
                        skippedResources.add(resource);
                    } else {
                        Files.copy(resource, target, StandardCopyOption.REPLACE_EXISTING);
                        copiedResources.add(resource);
                    }
                }
            } catch (IOException exception) {
                throw new ResourceCopyException(
                        "Could not copy resources from "
                                + resourceRoot
                                + " to "
                                + outputDirectory
                                + ". Check that the project directories are readable and writable.",
                        exception);
            }
        }
        return new ResourceCopyResult(copiedResources, skippedResources);
    }

    private static boolean isCurrent(Path source, Path target) throws IOException {
        return Files.isRegularFile(target)
                && Files.size(source) == Files.size(target)
                && Files.mismatch(source, target) == -1L;
    }

    private static Path resourceRoot(Path projectDirectory, String configuredRoot) {
        Path configured = Path.of(configuredRoot);
        Path projectRoot = projectDirectory.toAbsolutePath().normalize();
        Path path = projectRoot.resolve(configured).normalize();
        if (configured.isAbsolute() || !path.startsWith(projectRoot) || path.equals(projectRoot)) {
            throw new ResourceCopyException(
                    "Invalid resource root `"
                            + configuredRoot
                            + "`. Use a project-relative path under the project directory.");
        }
        return path;
    }

    private static boolean startsWithOutputDirectorySegment(Path relativePath) {
        if (relativePath.getNameCount() == 0) {
            return false;
        }
        return OUTPUT_DIRECTORY_NAMES.contains(relativePath.getName(0).toString());
    }
}
