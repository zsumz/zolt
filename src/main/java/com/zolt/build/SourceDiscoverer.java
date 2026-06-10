package com.zolt.build;

import com.zolt.project.BuildSettings;
import com.zolt.project.GeneratedSourceKind;
import com.zolt.project.GeneratedSourceStep;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

public final class SourceDiscoverer {
    private static final Set<String> OUTPUT_DIRECTORY_NAMES = Set.of("target", "build");

    public SourceDiscoveryResult discover(Path projectDirectory, BuildSettings settings) {
        Path projectRoot = projectDirectory.toAbsolutePath().normalize();
        Path output = projectDirectory.resolve(settings.output()).normalize();
        Path testOutput = projectDirectory.resolve(settings.testOutput()).normalize();
        List<Path> mainRoots = new ArrayList<>();
        mainRoots.add(projectDirectory.resolve(settings.source()).normalize());
        mainRoots.addAll(generatedRoots(projectRoot, settings.generatedMainSources(), "main"));
        List<Path> testRoots = new ArrayList<>(settings.testSources().stream()
                .map(root -> projectDirectory.resolve(root).normalize())
                .toList());
        testRoots.addAll(generatedRoots(projectRoot, settings.generatedTestSources(), "test"));
        return new SourceDiscoveryResult(
                discoverSources(mainRoots, output, testOutput, ".java"),
                discoverSources(testRoots, output, testOutput, ".java"),
                discoverSources(settings.groovyTestSources().stream()
                        .map(root -> projectDirectory.resolve(root).normalize())
                        .toList(), output, testOutput, ".groovy"));
    }

    private static List<Path> generatedRoots(
            Path projectRoot,
            List<GeneratedSourceStep> steps,
            String scope) {
        List<Path> roots = new ArrayList<>();
        for (GeneratedSourceStep step : steps) {
            if (step.kind() != GeneratedSourceKind.DECLARED_ROOT) {
                throw new SourceDiscoveryException(
                        "Unsupported generated source kind `"
                                + step.kind().configValue()
                                + "` for [generated."
                                + scope
                                + "."
                                + step.id()
                                + "]. Use declared-root for already generated Java sources.");
            }
            if (!"java".equals(step.language())) {
                throw new SourceDiscoveryException(
                        "Unsupported generated source language `"
                                + step.language()
                                + "` for [generated."
                                + scope
                                + "."
                                + step.id()
                                + "]. Supported generated source language is java.");
            }
            validateInputs(projectRoot, step, scope);
            Path output = safeProjectPath(projectRoot, step.output(), scope, step.id(), "output");
            if (!Files.isDirectory(output)) {
                if (step.required()) {
                    throw new SourceDiscoveryException(
                            "Generated source root `"
                                    + step.output()
                                    + "` is missing. Run the generator that produces it, commit the generated sources, or remove [generated."
                                    + scope
                                    + "."
                                    + step.id()
                                    + "] until Zolt supports that generator.");
                }
                continue;
            }
            roots.add(output);
        }
        return roots;
    }

    private static void validateInputs(Path projectRoot, GeneratedSourceStep step, String scope) {
        for (String input : step.inputs()) {
            safeProjectPath(projectRoot, input, scope, step.id(), "inputs");
        }
    }

    private static Path safeProjectPath(Path projectRoot, String configuredPath, String scope, String id, String field) {
        Path configured = Path.of(configuredPath);
        Path path = projectRoot.resolve(configured).normalize();
        if (configured.isAbsolute() || !path.startsWith(projectRoot) || path.equals(projectRoot)) {
            throw new SourceDiscoveryException(
                    "Invalid generated source "
                            + field
                            + " path `"
                            + configuredPath
                            + "` for [generated."
                            + scope
                            + "."
                            + id
                            + "]. Use a project-relative path under the project directory.");
        }
        return path;
    }

    private static List<Path> discoverSources(List<Path> roots, Path output, Path testOutput, String extension) {
        return roots.stream()
                .flatMap(root -> discoverSourcesFromRoot(root, output, testOutput, extension).stream())
                .distinct()
                .sorted()
                .toList();
    }

    private static List<Path> discoverSourcesFromRoot(Path root, Path output, Path testOutput, String extension) {
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        try (Stream<Path> paths = Files.walk(root)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(extension))
                    .map(Path::normalize)
                    .filter(path -> !path.startsWith(output))
                    .filter(path -> !path.startsWith(testOutput))
                    .filter(path -> !startsWithOutputDirectorySegment(root.relativize(path)))
                    .sorted()
                    .toList();
        } catch (IOException exception) {
            throw new SourceDiscoveryException(
                    "Could not discover sources under "
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
