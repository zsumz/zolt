package com.zolt.build;

import static com.zolt.build.IncrementalCompileInputHasher.hash;
import static com.zolt.build.IncrementalCompileInputHasher.hashText;
import static com.zolt.build.IncrementalCompileInputHasher.relative;

import com.zolt.classpath.Classpath;
import com.zolt.project.GeneratedSourceStep;
import com.zolt.project.ProjectConfig;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

final class IncrementalCompileStateValidator {
    String validate(
            IncrementalCompileState state,
            String scope,
            Path projectRoot,
            ProjectConfig config,
            List<String> configuredSourceRoots,
            List<GeneratedSourceStep> generatedSteps,
            Classpath compileClasspath,
            Path outputDirectory,
            Path generatedSourcesDirectory) {
        if (!state.scope().equals(scope)) {
            return "state-scope-mismatch";
        }
        if (!state.projectDirectory().equals(projectRoot)
                || !state.outputDirectory().equals(outputDirectory.toAbsolutePath().normalize())
                || !state.generatedSourcesDirectory().equals(generatedSourcesDirectory.toAbsolutePath().normalize())) {
            return "state-path-mismatch";
        }
        if (!state.compilerSettingsHash().equals(hashText(config.compilerSettings().toString()))) {
            return "compiler-settings-changed";
        }
        if (!state.sourceRoots().equals(sourceRoots(projectRoot, configuredSourceRoots, generatedSteps))) {
            return "source-roots-changed";
        }
        if (!state.generatedSourceRoots().equals(generatedSteps.stream().map(GeneratedSourceStep::output).sorted().toList())) {
            return "generated-source-roots-changed";
        }
        if (!state.compileClasspath().equals(classpathEntries(compileClasspath))) {
            return "compile-classpath-changed";
        }
        return "";
    }

    private static List<String> sourceRoots(
            Path projectRoot,
            List<String> configuredSourceRoots,
            List<GeneratedSourceStep> generatedSteps) {
        List<Path> roots = new ArrayList<>();
        configuredSourceRoots.stream()
                .map(root -> projectRoot.resolve(root).normalize())
                .forEach(roots::add);
        generatedSteps.stream()
                .map(step -> projectRoot.resolve(step.output()).normalize())
                .forEach(roots::add);
        return roots.stream()
                .distinct()
                .sorted()
                .map(path -> relative(projectRoot, path))
                .toList();
    }

    private static List<IncrementalCompileState.ClasspathEntry> classpathEntries(Classpath classpath) {
        return classpath.entries().stream()
                .map(path -> path.toAbsolutePath().normalize())
                .sorted()
                .map(path -> new IncrementalCompileState.ClasspathEntry(path, hash(path)))
                .toList();
    }
}
