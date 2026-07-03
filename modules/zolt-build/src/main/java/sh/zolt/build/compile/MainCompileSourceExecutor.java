package sh.zolt.build.compile;

import sh.zolt.build.BuildException;
import sh.zolt.build.CompileDiagnostics;
import sh.zolt.build.JavacException;
import sh.zolt.build.discovery.SourceDiscoveryResult;
import sh.zolt.build.incremental.IncrementalCompilePlan;
import sh.zolt.build.incremental.IncrementalCompilePlanner;
import sh.zolt.build.incremental.IncrementalCompileStateRecorder;
import sh.zolt.build.incremental.IncrementalCompileValidation;
import sh.zolt.classpath.Classpath;
import sh.zolt.classpath.ClasspathSet;
import sh.zolt.doctor.JdkStatus;
import sh.zolt.project.CompilerSettings;
import sh.zolt.project.ProjectConfig;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class MainCompileSourceExecutor {
    private final JavacRunner javacRunner;
    private final IncrementalCompileStateRecorder incrementalCompileStateRecorder;
    private final IncrementalCompilePlanner incrementalCompilePlanner;

    public MainCompileSourceExecutor(
            JavacRunner javacRunner,
            IncrementalCompileStateRecorder incrementalCompileStateRecorder,
            IncrementalCompilePlanner incrementalCompilePlanner) {
        this.javacRunner = javacRunner;
        this.incrementalCompileStateRecorder = incrementalCompileStateRecorder;
        this.incrementalCompilePlanner = incrementalCompilePlanner;
    }

    public Attempt compile(
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
        boolean hostMode = config.compilerSettings().mainHostPlatformApi()
                && !effectiveRelease(config).isBlank();
        CompilerPlatformApi.rejectModularHost(hostMode, sources.mainSources(), "main");
        JavacOptions options = javacOptions(config, sources.mainSources(), classpaths.compile(), hostMode);
        String platformApiWarning = CompilerPlatformApi.determinismWarning(hostMode, "main", jdkStatus);
        IncrementalCompilePlan plan = incrementalCompilePlanner.planMain(
                projectDirectory,
                config,
                sources.mainSources(),
                classpaths.compile(),
                classpaths.processor(),
                outputDirectory,
                generatedSourcesDirectory);
        if (plan.incremental()) {
            return withPlatformApiWarning(
                    incrementalCompile(
                            jdkStatus,
                            sources,
                            classpaths,
                            outputDirectory,
                            generatedSourcesDirectory,
                            options,
                            plan),
                    platformApiWarning);
        }
        incrementalCompileStateRecorder.deleteMainState(outputDirectory);
        deleteOwnedOutputs(plan);
        return withPlatformApiWarning(
                fullCompile(
                        jdkStatus,
                        sources,
                        classpaths,
                        outputDirectory,
                        generatedSourcesDirectory,
                        options,
                        plan.fallbackReason(),
                        plan.fullDiagnostics(sources.mainSources().size())),
                platformApiWarning);
    }

    private static Attempt withPlatformApiWarning(Attempt attempt, String warning) {
        if (warning == null || warning.isBlank()) {
            return attempt;
        }
        return new Attempt(
                new JavacResult(
                        attempt.result().sourceCount(),
                        attempt.result().outputDirectory(),
                        combinedOutput(warning, attempt.result().output())),
                attempt.mode(),
                attempt.fallbackReason(),
                attempt.diagnostics());
    }

    private Attempt incrementalCompile(
            JdkStatus jdkStatus,
            SourceDiscoveryResult sources,
            ClasspathSet classpaths,
            Path outputDirectory,
            Path generatedSourcesDirectory,
            JavacOptions options,
            IncrementalCompilePlan plan) {
        JavacResult result;
        IncrementalCompileValidation validation;
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

    private static JavacOptions javacOptions(
            ProjectConfig config, List<Path> mainSources, Classpath compileClasspath, boolean hostMode) {
        CompilerSettings compiler = config.compilerSettings();
        JavacOptions options = new JavacOptions(
                effectiveRelease(config),
                compiler.encoding(),
                compiler.args(),
                List.of(),
                hostMode);
        if (isModularSourceSet(mainSources)) {
            return options.withModulePath(compileClasspath.entries());
        }
        return options;
    }

    private static boolean isModularSourceSet(List<Path> mainSources) {
        for (Path source : mainSources) {
            Path fileName = source.getFileName();
            if (fileName != null && fileName.toString().equals("module-info.java")) {
                return true;
            }
        }
        return false;
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
                        "Could not delete stale compiled class "
                                + output
                                + ". Check that the build output directory is writable.",
                        exception);
            }
        }
    }

    public record Attempt(
            JavacResult result,
            String mode,
            String fallbackReason,
            CompileDiagnostics diagnostics) {
        public int sourceCount() {
            return result.sourceCount();
        }

        public Path outputDirectory() {
            return result.outputDirectory();
        }

        public String output() {
            return result.output();
        }
    }
}
