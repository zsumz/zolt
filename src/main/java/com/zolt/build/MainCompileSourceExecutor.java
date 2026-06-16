package com.zolt.build;

import com.zolt.classpath.Classpath;
import com.zolt.classpath.ClasspathSet;
import com.zolt.doctor.JdkStatus;
import com.zolt.project.CompilerSettings;
import com.zolt.project.ProjectConfig;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

final class MainCompileSourceExecutor {
    private final JavacRunner javacRunner;
    private final IncrementalCompileStateRecorder incrementalCompileStateRecorder;
    private final IncrementalCompilePlanner incrementalCompilePlanner;

    MainCompileSourceExecutor(
            JavacRunner javacRunner,
            IncrementalCompileStateRecorder incrementalCompileStateRecorder,
            IncrementalCompilePlanner incrementalCompilePlanner) {
        this.javacRunner = javacRunner;
        this.incrementalCompileStateRecorder = incrementalCompileStateRecorder;
        this.incrementalCompilePlanner = incrementalCompilePlanner;
    }

    Attempt compile(
            boolean compileSkipped,
            Path projectDirectory,
            ProjectConfig config,
            SourceDiscoveryResult sources,
            ClasspathSet classpaths,
            Path outputDirectory,
            Path generatedSourcesDirectory,
            JdkStatus jdkStatus) {
        if (compileSkipped) {
            return new Attempt(
                    new JavacResult(sources.mainSources().size(), outputDirectory, ""),
                    "skipped",
                    "",
                    CompileDiagnostics.empty());
        }
        JavacOptions options = javacOptions(config);
        IncrementalCompilePlanner.Plan plan = incrementalCompilePlanner.planMain(
                projectDirectory,
                config,
                sources.mainSources(),
                classpaths.compile(),
                classpaths.processor(),
                outputDirectory,
                generatedSourcesDirectory);
        if (plan.incremental()) {
            return incrementalCompile(
                    jdkStatus,
                    sources,
                    classpaths,
                    outputDirectory,
                    generatedSourcesDirectory,
                    options,
                    plan);
        }
        incrementalCompileStateRecorder.deleteMainState(outputDirectory);
        deleteOwnedOutputs(plan);
        return fullCompile(
                jdkStatus,
                sources,
                classpaths,
                outputDirectory,
                generatedSourcesDirectory,
                options,
                plan.fallbackReason(),
                plan.fullDiagnostics(sources.mainSources().size()));
    }

    private Attempt incrementalCompile(
            JdkStatus jdkStatus,
            SourceDiscoveryResult sources,
            ClasspathSet classpaths,
            Path outputDirectory,
            Path generatedSourcesDirectory,
            JavacOptions options,
            IncrementalCompilePlanner.Plan plan) {
        JavacResult result;
        IncrementalCompilePlanner.IncrementalValidation validation;
        try {
            result = javacRunner.compile(
                    jdkStatus.javac().orElseThrow(),
                    plan.sourcesToCompile(),
                    incrementalClasspath(classpaths.compile(), outputDirectory),
                    outputDirectory,
                    classpaths.processor(),
                    generatedSourcesDirectory,
                    options);
            validation = incrementalCompilePlanner.validateAfterIncrementalCompile(plan);
            if (!validation.hasFallback() && !validation.additionalSources().isEmpty()) {
                JavacResult dependentResult = javacRunner.compile(
                        jdkStatus.javac().orElseThrow(),
                        validation.additionalSources(),
                        incrementalClasspath(classpaths.compile(), outputDirectory),
                        outputDirectory,
                        classpaths.processor(),
                        generatedSourcesDirectory,
                        options);
                result = new JavacResult(
                        result.sourceCount() + dependentResult.sourceCount(),
                        outputDirectory,
                        combinedOutput(result.output(), dependentResult.output()));
            }
        } catch (JavacException exception) {
            incrementalCompileStateRecorder.deleteMainState(outputDirectory);
            return fullCompile(
                    jdkStatus,
                    sources,
                    classpaths,
                    outputDirectory,
                    generatedSourcesDirectory,
                    options,
                    "incremental-javac-failed",
                    plan.fullDiagnostics(sources.mainSources().size()));
        }
        if (!validation.hasFallback()) {
            return new Attempt(result, "incremental", "", plan.diagnostics(result.sourceCount(), validation));
        }
        incrementalCompileStateRecorder.deleteMainState(outputDirectory);
        deleteOwnedOutputs(plan);
        return fullCompile(
                jdkStatus,
                sources,
                classpaths,
                outputDirectory,
                generatedSourcesDirectory,
                options,
                validation.fallbackReason(),
                plan.fullDiagnostics(sources.mainSources().size()));
    }

    private Attempt fullCompile(
            JdkStatus jdkStatus,
            SourceDiscoveryResult sources,
            ClasspathSet classpaths,
            Path outputDirectory,
            Path generatedSourcesDirectory,
            JavacOptions options,
            String fallbackReason,
            CompileDiagnostics diagnostics) {
        return new Attempt(
                javacRunner.compile(
                        jdkStatus.javac().orElseThrow(),
                        sources.mainSources(),
                        classpaths.compile(),
                        outputDirectory,
                        classpaths.processor(),
                        generatedSourcesDirectory,
                        options),
                "full",
                fallbackReason,
                diagnostics);
    }

    private static String combinedOutput(String first, String second) {
        if (first == null || first.isEmpty()) {
            return second == null ? "" : second;
        }
        if (second == null || second.isEmpty()) {
            return first;
        }
        if (first.endsWith("\n")) {
            return first + second;
        }
        return first + "\n" + second;
    }

    private static JavacOptions javacOptions(ProjectConfig config) {
        CompilerSettings compiler = config.compilerSettings();
        return new JavacOptions(
                effectiveRelease(config),
                compiler.encoding(),
                compiler.args());
    }

    private static String effectiveRelease(ProjectConfig config) {
        String compilerRelease = config.compilerSettings().release();
        return compilerRelease.isBlank() ? config.project().java() : compilerRelease;
    }

    private static Classpath incrementalClasspath(Classpath classpath, Path outputDirectory) {
        List<Path> entries = new ArrayList<>();
        entries.add(outputDirectory);
        entries.addAll(classpath.entries());
        return new Classpath(entries);
    }

    private static void deleteOwnedOutputs(IncrementalCompilePlanner.Plan plan) {
        for (Path output : plan.outputsToDelete()) {
            try {
                Files.deleteIfExists(output);
            } catch (IOException exception) {
                throw new BuildException(
                        "Could not delete stale compiled class "
                                + output
                                + ". Check that the build output directory is writable.",
                        exception);
            }
        }
    }

    record Attempt(
            JavacResult result,
            String mode,
            String fallbackReason,
            CompileDiagnostics diagnostics) {
        int sourceCount() {
            return result.sourceCount();
        }

        Path outputDirectory() {
            return result.outputDirectory();
        }

        String output() {
            return result.output();
        }
    }
}
