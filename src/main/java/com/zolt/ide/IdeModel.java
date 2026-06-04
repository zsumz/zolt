package com.zolt.ide;

import java.nio.file.Path;
import java.util.List;

public record IdeModel(
        int schemaVersion,
        ProjectInfo project,
        JavaInfo java,
        PathInfo paths,
        List<SourceRoot> sourceRoots,
        List<ResourceRoot> resourceRoots,
        OutputInfo outputs,
        DependencyInfo dependencies,
        ClasspathInfo classpaths,
        List<Diagnostic> diagnostics) {
    public IdeModel {
        sourceRoots = List.copyOf(sourceRoots);
        resourceRoots = List.copyOf(resourceRoots);
        diagnostics = List.copyOf(diagnostics);
    }

    public record ProjectInfo(
            String name,
            String group,
            String version,
            String mainClass) {
    }

    public record JavaInfo(
            String version,
            String detectedVersion,
            String javaHome) {
    }

    public record PathInfo(
            Path root,
            Path config,
            Path lockfile) {
    }

    public record SourceRoot(
            String id,
            String kind,
            String language,
            Path path,
            boolean generated) {
    }

    public record ResourceRoot(
            String id,
            String kind,
            Path path) {
    }

    public record OutputInfo(
            Path mainClasses,
            Path testClasses,
            Path packagePath) {
    }

    public record DependencyInfo(
            List<DependencyDeclaration> api,
            List<DependencyDeclaration> implementation,
            List<DependencyDeclaration> test,
            List<DependencyDeclaration> annotationProcessors,
            List<DependencyDeclaration> testAnnotationProcessors) {
        public DependencyInfo {
            api = List.copyOf(api);
            implementation = List.copyOf(implementation);
            test = List.copyOf(test);
            annotationProcessors = List.copyOf(annotationProcessors);
            testAnnotationProcessors = List.copyOf(testAnnotationProcessors);
        }
    }

    public record DependencyDeclaration(
            String coordinate,
            String version,
            boolean managed,
            String workspace) {
    }

    public record ClasspathInfo(
            List<Path> compile,
            List<Path> runtime,
            List<Path> test) {
        public ClasspathInfo {
            compile = List.copyOf(compile);
            runtime = List.copyOf(runtime);
            test = List.copyOf(test);
        }
    }

    public record Diagnostic(
            String severity,
            String code,
            String message,
            Path path,
            String nextStep) {
    }
}
