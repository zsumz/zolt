package com.zolt.ide;

import java.nio.file.Path;
import java.util.List;

public record IdeModel(
        int schemaVersion,
        ProjectInfo project,
        JavaInfo java,
        CompilerInfo compiler,
        PackageInfo packageInfo,
        PathInfo paths,
        List<SourceRoot> sourceRoots,
        List<ResourceRoot> resourceRoots,
        OutputInfo outputs,
        DependencyInfo dependencies,
        ClasspathInfo classpaths,
        FrameworkInfo frameworks,
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

    public record CompilerInfo(
            String release,
            String effectiveRelease,
            String encoding,
            List<String> args,
            List<String> testArgs,
            Path generatedSources,
            Path generatedTestSources) {
        public CompilerInfo {
            args = List.copyOf(args);
            testArgs = List.copyOf(testArgs);
        }
    }

    public record PackageInfo(
            String mode,
            boolean sources,
            boolean javadoc,
            boolean tests,
            Path mainJar,
            Path sourcesJar,
            Path javadocJar,
            Path testsJar,
            PublicationInfo metadata) {
    }

    public record PublicationInfo(
            String name,
            String description,
            String url,
            String license,
            List<String> developers,
            String scm,
            String issues) {
        public PublicationInfo {
            developers = List.copyOf(developers);
        }
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
            List<DependencyDeclaration> runtime,
            List<DependencyDeclaration> provided,
            List<DependencyDeclaration> dev,
            List<DependencyDeclaration> test,
            List<DependencyDeclaration> annotationProcessors,
            List<DependencyDeclaration> testAnnotationProcessors) {
        public DependencyInfo {
            api = List.copyOf(api);
            implementation = List.copyOf(implementation);
            runtime = List.copyOf(runtime);
            provided = List.copyOf(provided);
            dev = List.copyOf(dev);
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

    public record FrameworkInfo(
            QuarkusInfo quarkus) {
    }

    public record QuarkusInfo(
            boolean enabled,
            String packageMode,
            String augmentationStatus,
            String inputFingerprint,
            String recordedInputFingerprint,
            Path augmentationMetadata,
            Path augmentationDirectory,
            Path packageDirectory,
            Path runnerJar,
            Path generatedBytecodeJar,
            Path transformedBytecodeJar,
            List<Path> deploymentClasspath) {
        public QuarkusInfo {
            deploymentClasspath = List.copyOf(deploymentClasspath);
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
