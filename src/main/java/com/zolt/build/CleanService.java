package com.zolt.build;

import com.zolt.project.BuildSettings;
import com.zolt.project.CompilerSettings;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

public final class CleanService {
    public CleanResult clean(Path projectDirectory, BuildSettings settings) {
        return clean(projectDirectory, settings, CompilerSettings.defaults());
    }

    public CleanResult clean(Path projectDirectory, BuildSettings settings, CompilerSettings compilerSettings) {
        Path projectRoot = projectDirectory.toAbsolutePath().normalize();
        Set<Path> targets = cleanTargets(projectRoot, settings, compilerSettings);
        ArrayList<Path> deleted = new ArrayList<>();
        for (Path target : targets) {
            if (!Files.exists(target)) {
                continue;
            }
            deleteRecursively(target);
            deleted.add(target);
        }
        return new CleanResult(deleted);
    }

    private static Set<Path> cleanTargets(Path projectRoot, BuildSettings settings, CompilerSettings compilerSettings) {
        Path output = safeProjectPath(projectRoot, settings.output());
        Path testOutput = safeProjectPath(projectRoot, settings.testOutput());
        Path generatedSources = safeProjectPath(projectRoot, compilerSettings.generatedSources());
        Path generatedTestSources = safeProjectPath(projectRoot, compilerSettings.generatedTestSources());
        Path sharedParent = sharedOutputParent(output, testOutput).orElse(null);
        Set<Path> targets = new LinkedHashSet<>();
        if (sharedParent != null && isBuildOutputParent(sharedParent)) {
            targets.add(sharedParent);
        } else {
            targets.add(output);
            targets.add(testOutput);
        }
        targets.add(generatedSources);
        targets.add(generatedTestSources);
        return targets;
    }

    private static Optional<Path> sharedOutputParent(Path output, Path testOutput) {
        Path outputParent = output.getParent();
        Path testOutputParent = testOutput.getParent();
        if (outputParent != null && outputParent.equals(testOutputParent)) {
            return Optional.of(outputParent);
        }
        return Optional.empty();
    }

    private static boolean isBuildOutputParent(Path path) {
        Path name = path.getFileName();
        return name != null && ("target".equals(name.toString()) || "build".equals(name.toString()));
    }

    private static Path safeProjectPath(Path projectRoot, String configuredPath) {
        Path configured = Path.of(configuredPath);
        Path path = projectRoot.resolve(configured).normalize();
        if (configured.isAbsolute() || !path.startsWith(projectRoot) || path.equals(projectRoot)) {
            throw new CleanException(
                    "Refusing to clean output path "
                            + configuredPath
                            + " because it is outside the project or points at the project root.");
        }
        return path;
    }

    private static void deleteRecursively(Path target) {
        try (Stream<Path> paths = Files.walk(target)) {
            List<Path> sorted = paths.sorted(Comparator.reverseOrder()).toList();
            for (Path path : sorted) {
                Files.deleteIfExists(path);
            }
        } catch (IOException exception) {
            throw new CleanException(
                    "Could not delete build output at "
                            + target
                            + ". Check filesystem permissions and try again.",
                    exception);
        }
    }
}
