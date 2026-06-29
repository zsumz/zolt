package com.zolt.build;

import com.zolt.build.incremental.IncrementalCompilePlan;
import com.zolt.build.incremental.IncrementalCompilePlanner;
import com.zolt.build.incremental.IncrementalCompileStateRecorder;
import com.zolt.build.incremental.IncrementalCompileValidation;
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

final class TestCompileSourceExecutor {
    private final JavacRunner javacRunner;
    private final GroovyCompilerRunner groovyCompilerRunner;
    private final IncrementalCompileStateRecorder incrementalCompileStateRecorder;
    private final IncrementalCompilePlanner incrementalCompilePlanner;

    TestCompileSourceExecutor(
            JavacRunner javacRunner,
            GroovyCompilerRunner groovyCompilerRunner,
            IncrementalCompileStateRecorder incrementalCompileStateRecorder,
            IncrementalCompilePlanner incrementalCompilePlanner) {
        this.javacRunner = javacRunner;
        this.groovyCompilerRunner = groovyCompilerRunner;
        this.incrementalCompileStateRecorder = incrementalCompileStateRecorder;
        this.incrementalCompilePlanner = incrementalCompilePlanner;
    }

    Attempt compile(
            boolean compileSkipped,
            Path projectDirectory,
            ProjectConfig config,
            SourceDiscoveryResult sources,
            ClasspathSet classpaths,
            Classpath testCompileClasspath,
            Classpath groovyCompileClasspath,
            Path outputDirectory,
            Path generatedSourcesDirectory,
            JdkStatus jdkStatus) {
        if (compileSkipped) {
            return new Attempt(
                    new JavacResult(sources.testSources().size(), outputDirectory, ""),
                    new JavacResult(sources.groovyTestSources().size(), outputDirectory, ""),
                    "skipped",
                    "",
                    CompileDiagnostics.empty());
        }
        JavacOptions options = javacOptions(config);
        IncrementalCompilePlan plan = incrementalCompilePlanner.planTest(
                projectDirectory,
                config,
                sources,
                testCompileClasspath,
                classpaths.testProcessor(),
                outputDirectory,
                generatedSourcesDirectory);
        if (plan.incremental()) {
            return incrementalCompile(
                    jdkStatus,
                    sources,
                    testCompileClasspath,
                    groovyCompileClasspath,
                    outputDirectory,
                    generatedSourcesDirectory,
                    classpaths,
                    options,
                    plan);
        }
        incrementalCompileStateRecorder.deleteTestState(outputDirectory);
        deleteOwnedOutputs(plan);
        return fullTestCompile(
                jdkStatus,
                sources,
                testCompileClasspath,
                groovyCompileClasspath,
                outputDirectory,
                generatedSourcesDirectory,
                classpaths,
                options,
                plan.fallbackReason(),
                plan.fullDiagnostics(sources.testSources().size() + sources.groovyTestSources().size()));
    }

    private Attempt incrementalCompile(
            JdkStatus jdkStatus,
            SourceDiscoveryResult sources,
            Classpath testCompileClasspath,
            Classpath groovyCompileClasspath,
            Path outputDirectory,
            Path generatedSourcesDirectory,
            ClasspathSet classpaths,
            JavacOptions options,
            IncrementalCompilePlan plan) {
        JavacResult javacResult;
        IncrementalCompileValidation validation;
        try {
            javacResult = javacRunner.compile(
                    jdkStatus.javac().orElseThrow(),
                    plan.sourcesToCompile(),
                    incrementalClasspath(testCompileClasspath, outputDirectory),
                    outputDirectory,
                    classpaths.testProcessor(),
                    generatedSourcesDirectory,
                    options);
            validation = incrementalCompilePlanner.validateAfterIncrementalCompile(plan);
            if (!validation.hasFallback() && !validation.additionalSources().isEmpty()) {
                JavacResult dependentResult = javacRunner.compile(
                        jdkStatus.javac().orElseThrow(),
                        validation.additionalSources(),
                        incrementalClasspath(testCompileClasspath, outputDirectory),
                        outputDirectory,
                        classpaths.testProcessor(),
                        generatedSourcesDirectory,
                        options);
                javacResult = new JavacResult(
                        javacResult.sourceCount() + dependentResult.sourceCount(),
                        outputDirectory,
                        combinedOutput(javacResult.output(), dependentResult.output()));
            }
        } catch (JavacException exception) {
            incrementalCompileStateRecorder.deleteTestState(outputDirectory);
            return fullTestCompile(
                    jdkStatus,
                    sources,
                    testCompileClasspath,
                    groovyCompileClasspath,
                    outputDirectory,
                    generatedSourcesDirectory,
                    classpaths,
                    options,
                    "incremental-javac-failed",
                    plan.fullDiagnostics(sources.testSources().size() + sources.groovyTestSources().size()));
        }
        if (!validation.hasFallback()) {
            return new Attempt(
                    javacResult,
                    new JavacResult(0, outputDirectory, ""),
                    "incremental",
                    "",
                    plan.diagnostics(javacResult.sourceCount(), validation));
        }
        incrementalCompileStateRecorder.deleteTestState(outputDirectory);
        deleteOwnedOutputs(plan);
        return fullTestCompile(
                jdkStatus,
                sources,
                testCompileClasspath,
                groovyCompileClasspath,
                outputDirectory,
                generatedSourcesDirectory,
                classpaths,
                options,
                validation.fallbackReason(),
                plan.fullDiagnostics(sources.testSources().size() + sources.groovyTestSources().size()));
    }

    private Attempt fullTestCompile(
            JdkStatus jdkStatus,
            SourceDiscoveryResult sources,
            Classpath testCompileClasspath,
            Classpath groovyCompileClasspath,
            Path outputDirectory,
            Path generatedSourcesDirectory,
            ClasspathSet classpaths,
            JavacOptions options,
            String fallbackReason,
            CompileDiagnostics diagnostics) {
        JavacResult javacResult = javacRunner.compile(
                jdkStatus.javac().orElseThrow(),
                sources.testSources(),
                testCompileClasspath,
                outputDirectory,
                classpaths.testProcessor(),
                generatedSourcesDirectory,
                options);
        JavacResult groovyResult = groovyCompilerRunner.compile(
                jdkStatus.java().orElseThrow(),
                sources.groovyTestSources(),
                groovyCompileClasspath,
                outputDirectory);
        return new Attempt(javacResult, groovyResult, "full", fallbackReason, diagnostics);
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
                compiler.testArgs());
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

    private static void deleteOwnedOutputs(IncrementalCompilePlan plan) {
        for (Path output : plan.outputsToDelete()) {
            try {
                Files.deleteIfExists(output);
            } catch (IOException exception) {
                throw new BuildException(
                        "Could not delete stale compiled test class "
                                + output
                                + ". Check that the test output directory is writable.",
                        exception);
            }
        }
    }

    record Attempt(JavacResult javacResult,
            JavacResult groovyResult,
            String mode,
            String fallbackReason,
            CompileDiagnostics diagnostics) {
        int sourceCount() {
            return javacResult.sourceCount() + groovyResult.sourceCount();
        }

        Path outputDirectory() {
            return javacResult.outputDirectory();
        }

        String output() {
            return combinedOutput(javacResult.output(), groovyResult.output());
        }
    }
}
