package sh.zolt.build.generatedsource;

import sh.zolt.build.BuildException;
import sh.zolt.classpath.ResolvedClasspathPackage;
import sh.zolt.dependency.DependencyScope;
import sh.zolt.project.GeneratedSourceStep;
import sh.zolt.project.ProjectConfig;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/** Classpath, cwd, and output-directory resolution for an exec step, factored out of the service. */
final class ExecStepWorkspace {
    private ExecStepWorkspace() {
    }

    /** The locked tool-exec jars for jvm-runner steps, sorted for a stable classpath. */
    static List<Path> toolClasspath(List<ResolvedClasspathPackage> packages) {
        return packages.stream()
                .filter(dependency -> dependency.scope() == DependencyScope.TOOL_EXEC)
                .map(dependency -> dependency.resolvedPackage().jarPath())
                .distinct()
                .sorted()
                .toList();
    }

    /** The project pseudo-tool classpath: the member's compiled classes first, then its runtime deps. */
    static List<Path> projectClasspath(
            Path root, ProjectConfig config, List<ResolvedClasspathPackage> packages, String scope) {
        List<Path> entries = new ArrayList<>();
        if ("test".equals(scope)) {
            entries.add(root.resolve(config.build().testOutput()).normalize());
            entries.add(root.resolve(config.build().output()).normalize());
            packages.stream()
                    .filter(dependency -> dependency.scope().entersTestClasspath())
                    .map(dependency -> dependency.resolvedPackage().jarPath())
                    .forEach(entries::add);
        } else {
            entries.add(root.resolve(config.build().output()).normalize());
            packages.stream()
                    .filter(dependency -> dependency.scope().entersMainRuntimeClasspath())
                    .map(dependency -> dependency.resolvedPackage().jarPath())
                    .forEach(entries::add);
        }
        return entries.stream().distinct().toList();
    }

    static Path resolveCwd(Path root, GeneratedSourceStep step, String subject) {
        if (step.exec().cwd().isEmpty()) {
            return root;
        }
        String configured = step.exec().cwd().orElseThrow();
        Path resolved = root.resolve(configured).normalize();
        if (!resolved.startsWith(root)) {
            throw BuildException.actionable(
                    "Exec step " + subject + " cwd `" + configured + "` escapes the project directory.",
                    "Use a project-relative directory under the project root.");
        }
        if (!Files.isDirectory(resolved)) {
            throw BuildException.actionable(
                    "Exec step " + subject + " cwd `" + configured + "` is not an existing directory.",
                    "Create the directory or fix " + subject + ".cwd.");
        }
        return resolved;
    }

    static void deleteOutput(Path output) {
        if (!Files.exists(output)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(output)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        } catch (IOException exception) {
            throw new BuildException(
                    "Could not clean exec output " + output + ". Check filesystem permissions and retry `zolt build`.",
                    exception);
        }
    }

    static void createDirectory(Path path) {
        try {
            Files.createDirectories(path);
        } catch (IOException exception) {
            throw new BuildException(
                    "Could not create exec output directory " + path + ". Check filesystem permissions.", exception);
        }
    }
}
