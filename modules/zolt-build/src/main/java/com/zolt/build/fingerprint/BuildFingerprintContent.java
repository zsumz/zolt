package com.zolt.build.fingerprint;

import com.zolt.build.BuildException;
import com.zolt.classpath.Classpath;
import com.zolt.project.BuildSettings;
import com.zolt.project.GeneratedSourceStep;
import com.zolt.project.ProjectConfig;
import com.zolt.project.ProjectPathException;
import com.zolt.project.ProjectPaths;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

final class BuildFingerprintContent {
    private static final String VERSION = "1";
    private static final List<String> OUTPUT_DIRECTORY_NAMES = List.of("build", "target");

    private final BuildFingerprintExpectedClasses expectedClasses = new BuildFingerprintExpectedClasses();
    private final BuildFingerprintFileHasher fileHasher = new BuildFingerprintFileHasher();

    String fingerprint(
            Path projectDirectory,
            ProjectConfig config,
            Path lockfilePath,
            List<String> sourceRoots,
            List<String> resourceRoots,
            String resourceRootKey,
            List<Path> sources,
            List<GeneratedSourceStep> generatedSteps,
            Classpath compileClasspath,
            Classpath processorClasspath,
            Path outputDirectory,
            String outputDirectoryName,
            Path generatedSourcesDirectory,
            BuildFingerprintState cachedState,
            Map<Path, BuildFingerprintCachedFileHash> collectedState) {
        Path projectRoot = projectDirectory.toAbsolutePath().normalize();
        StringBuilder content = new StringBuilder();
        line(content, "version", VERSION);
        line(content, "projectConfig", fileHasher.hashText(config.toString()));
        line(content, "zoltToml", fileHasher.fileHash(projectRoot.resolve("zolt.toml"), cachedState, collectedState));
        line(content, "lockfile", fileHasher.fileHash(lockfilePath, cachedState, collectedState));
        section(content, "sourceRoots", sourceRoots.stream().map(BuildFingerprintContent::normalize).toList());
        line(content, "outputDirectory", normalize(outputDirectoryName));
        line(content, "generatedSourcesDirectory", fileHasher.relative(projectRoot, generatedSourcesDirectory));
        line(content, "compilerSettings", config.compilerSettings().toString());
        section(content, "compileClasspath", classpathEntries(compileClasspath, cachedState, collectedState));
        section(content, "processorClasspath", classpathEntries(processorClasspath, cachedState, collectedState));
        section(content, "sources", fileEntries(projectRoot, sources, cachedState, collectedState));
        section(content, "generatedSourceInputs", generatedSourceInputEntries(projectRoot, generatedSteps, cachedState, collectedState));
        section(content, "resources", resourceEntries(projectRoot, resourceRoots, resourceRootKey, config.build(), cachedState, collectedState));
        section(content, "generatedSources", generatedSourceEntries(projectRoot, generatedSourcesDirectory, cachedState, collectedState));
        section(content, "expectedClasses", expectedClasses.entries(projectRoot, sourceRoots, sources, outputDirectory));
        return content.toString();
    }

    private List<String> classpathEntries(
            Classpath classpath,
            BuildFingerprintState cachedState,
            Map<Path, BuildFingerprintCachedFileHash> collectedState) {
        return classpath.entries().stream()
                .map(path -> path.toAbsolutePath().normalize())
                .sorted()
                .map(path -> path + "|" + fileHasher.classpathHash(path, cachedState, collectedState))
                .toList();
    }

    private List<String> fileEntries(
            Path projectRoot,
            List<Path> files,
            BuildFingerprintState cachedState,
            Map<Path, BuildFingerprintCachedFileHash> collectedState) {
        return files.stream()
                .map(path -> path.toAbsolutePath().normalize())
                .sorted()
                .map(path -> fileHasher.relative(projectRoot, path) + "|"
                        + fileHasher.fileHash(path, cachedState, collectedState))
                .toList();
    }

    private List<String> resourceEntries(
            Path projectRoot,
            List<String> configuredRoots,
            String resourceRootKey,
            BuildSettings settings,
            BuildFingerprintState cachedState,
            Map<Path, BuildFingerprintCachedFileHash> collectedState) {
        List<Path> resources = new ArrayList<>();
        Path mainOutput = outputPath(projectRoot, "[build].output", settings.output());
        Path testOutput = outputPath(projectRoot, "[build].testOutput", settings.testOutput());
        for (String configuredRoot : configuredRoots) {
            Path root = existingRoot(projectRoot, resourceRootKey, configuredRoot);
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (Stream<Path> paths = Files.walk(root)) {
                paths.filter(Files::isRegularFile)
                        .map(Path::normalize)
                        .filter(path -> !path.getFileName().toString().endsWith(".java"))
                        .filter(path -> !path.startsWith(mainOutput))
                        .filter(path -> !path.startsWith(testOutput))
                        .filter(path -> !startsWithOutputDirectorySegment(root.relativize(path)))
                        .forEach(resources::add);
            } catch (IOException exception) {
                throw new BuildException(
                        "Could not fingerprint resources under "
                                + root
                                + ". Check that the directory is readable.",
                        exception);
            }
        }
        return fileEntries(projectRoot, resources, cachedState, collectedState);
    }

    private List<String> generatedSourceInputEntries(
            Path projectRoot,
            List<GeneratedSourceStep> steps,
            BuildFingerprintState cachedState,
            Map<Path, BuildFingerprintCachedFileHash> collectedState) {
        return steps.stream()
                .flatMap(step -> step.inputs().stream()
                        .map(input -> inputPath(projectRoot, "[generated." + step.id() + "].inputs", input)))
                .distinct()
                .sorted()
                .map(path -> fileHasher.relative(projectRoot, path) + "|"
                        + fileHasher.fileHash(path, cachedState, collectedState))
                .toList();
    }

    private List<String> generatedSourceEntries(
            Path projectRoot,
            Path generatedSourcesDirectory,
            BuildFingerprintState cachedState,
            Map<Path, BuildFingerprintCachedFileHash> collectedState) {
        if (!Files.isDirectory(generatedSourcesDirectory)) {
            return List.of();
        }
        try (Stream<Path> paths = Files.walk(generatedSourcesDirectory)) {
            return fileEntries(
                    projectRoot,
                    paths.filter(Files::isRegularFile)
                            .map(Path::normalize)
                            .filter(path -> path.getFileName().toString().endsWith(".java"))
                            .toList(),
                    cachedState,
                    collectedState);
        } catch (IOException exception) {
            throw new BuildException(
                    "Could not fingerprint generated sources under "
                            + generatedSourcesDirectory
                            + ". Check that the directory is readable.",
                    exception);
        }
    }

    private static void section(StringBuilder content, String name, List<String> entries) {
        content.append('[').append(name).append(']').append('\n');
        entries.stream().sorted(Comparator.naturalOrder()).forEach(entry -> content.append(entry).append('\n'));
    }

    private static void line(StringBuilder content, String name, String value) {
        content.append(name).append('=').append(value).append('\n');
    }

    private static String normalize(String value) {
        return Path.of(value).normalize().toString().replace('\\', '/');
    }

    private static Path inputPath(Path projectRoot, String key, String configuredPath) {
        try {
            return ProjectPaths.input(projectRoot, key, configuredPath);
        } catch (ProjectPathException exception) {
            throw new BuildException(exception.getMessage(), exception);
        }
    }

    private static Path existingRoot(Path projectRoot, String key, String configuredPath) {
        try {
            return ProjectPaths.existingRoot(projectRoot, key, configuredPath);
        } catch (ProjectPathException exception) {
            throw new BuildException(exception.getMessage(), exception);
        }
    }

    private static Path outputPath(Path projectRoot, String key, String configuredPath) {
        try {
            return ProjectPaths.output(projectRoot, key, configuredPath);
        } catch (ProjectPathException exception) {
            throw new BuildException(exception.getMessage(), exception);
        }
    }

    private static boolean startsWithOutputDirectorySegment(Path relativePath) {
        return relativePath.getNameCount() > 0
                && OUTPUT_DIRECTORY_NAMES.contains(relativePath.getName(0).toString());
    }
}
