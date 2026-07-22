package sh.zolt.build.compile;

import sh.zolt.build.BuildException;
import sh.zolt.build.CompileDiagnostics;
import sh.zolt.build.JavacException;
import sh.zolt.build.discovery.SourceDiscoveryResult;
import sh.zolt.build.incremental.GeneratedOutputAttribution;
import sh.zolt.build.incremental.IncrementalCompilePlan;
import sh.zolt.build.incremental.IncrementalCompilePlanner;
import sh.zolt.build.incremental.IncrementalCompileStateRecorder;
import sh.zolt.build.incremental.IncrementalCompileWaveResult;
import sh.zolt.build.incremental.IncrementalDependentCompiler;
import sh.zolt.classpath.Classpath;
import sh.zolt.classpath.ClasspathSet;
import sh.zolt.doctor.JdkStatus;
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
        return compile(
                compileSkipped,
                "non-source-input-changed",
                projectDirectory,
                config,
                sources,
                classpaths,
                outputDirectory,
                generatedSourcesDirectory,
                jdkStatus);
    }

    public Attempt compile(
            boolean compileSkipped,
            String fingerprintMissReason,
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
                && !MainCompileOptions.effectiveRelease(config).isBlank();
        CompilerPlatformApi.rejectModularHost(hostMode, sources.mainSources(), "main");
        JavacOptions options = MainCompileOptions.forMainSources(
                config, sources.mainSources(), classpaths.compile(), hostMode);
        String platformApiWarning = CompilerPlatformApi.determinismWarning(hostMode, "main", jdkStatus);
        IncrementalCompilePlan plan = incrementalCompilePlanner.planMain(
                projectDirectory,
                config,
                sources.mainSources(),
                classpaths.compile(),
                classpaths.processor(),
                outputDirectory,
                generatedSourcesDirectory,
                fingerprintMissReason);
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
                        plan.fullDiagnostics(sources.mainSources().size()),
                        plan.captureProcessorAttribution()),
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
                attempt.diagnostics(),
                attempt.attribution(),
                attempt.compiledSources());
    }

    private Attempt incrementalCompile(
            JdkStatus jdkStatus,
            SourceDiscoveryResult sources,
            ClasspathSet classpaths,
            Path outputDirectory,
            Path generatedSourcesDirectory,
            JavacOptions options,
            IncrementalCompilePlan plan) {
        boolean captureAttribution = plan.captureProcessorAttribution();
        List<Path> compiledSources = new ArrayList<>(plan.sourcesToCompile());
        GeneratedOutputAttribution[] attribution = {GeneratedOutputAttribution.absent()};
        JavacResult result;
        IncrementalCompileWaveResult waves;
        try {
            deleteStaleOutputs(plan, plan.sourcesToCompile());
            deleteOutputs(plan.previousGeneratedOutputs(plan.sourcesToCompile()));
            result = javacRunner.compile(
                    jdkStatus.javac().orElseThrow(),
                    plan.sourcesToCompile(),
                    incrementalClasspath(classpaths.compile(), outputDirectory),
                    outputDirectory,
                    classpaths.processor(),
                    generatedSourcesDirectory,
                    options,
                    captureAttribution);
            attribution[0] = result.attribution();
            waves = incrementalCompilePlanner.validateAndCompileDependents(
                    plan,
                    dependentSources -> {
                        deleteStaleOutputs(plan, dependentSources);
                        deleteOutputs(plan.previousGeneratedOutputs(dependentSources));
                        JavacResult dependentResult = javacRunner.compile(
                                jdkStatus.javac().orElseThrow(),
                                dependentSources,
                                incrementalClasspath(classpaths.compile(), outputDirectory),
                                outputDirectory,
                                classpaths.processor(),
                                generatedSourcesDirectory,
                                options,
                                captureAttribution);
                        attribution[0] = attribution[0].merge(dependentResult.attribution());
                        compiledSources.addAll(dependentSources);
                        return new IncrementalDependentCompiler.Outcome(
                                dependentResult.sourceCount(), dependentResult.output());
                    });
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
                    plan.fullDiagnostics(sources.mainSources().size()),
                    captureAttribution);
        }
        if (waves.hasFallback()) {
            return fullFallback(
                    jdkStatus, sources, classpaths, outputDirectory, generatedSourcesDirectory,
                    options, plan, waves.validation().fallbackReason());
        }
        if (attribution[0].present() && attribution[0].unattributed()) {
            return fullFallback(
                    jdkStatus, sources, classpaths, outputDirectory, generatedSourcesDirectory,
                    options, plan, "processor-unattributed-output");
        }
        JavacResult combined = new JavacResult(
                result.sourceCount() + waves.dependentSourceCount(),
                outputDirectory,
                combinedOutput(result.output(), waves.dependentOutput()),
                attribution[0]);
        return new Attempt(
                combined,
                "incremental",
                "",
                plan.diagnostics(combined.sourceCount(), waves.validation()),
                attribution[0],
                compiledSources);
    }

    private Attempt fullFallback(
            JdkStatus jdkStatus,
            SourceDiscoveryResult sources,
            ClasspathSet classpaths,
            Path outputDirectory,
            Path generatedSourcesDirectory,
            JavacOptions options,
            IncrementalCompilePlan plan,
            String fallbackReason) {
        incrementalCompileStateRecorder.deleteMainState(outputDirectory);
        deleteOwnedOutputs(plan);
        return fullCompile(
                jdkStatus,
                sources,
                classpaths,
                outputDirectory,
                generatedSourcesDirectory,
                options,
                fallbackReason,
                plan.fullDiagnostics(sources.mainSources().size()),
                plan.captureProcessorAttribution());
    }

    private Attempt fullCompile(
            JdkStatus jdkStatus,
            SourceDiscoveryResult sources,
            ClasspathSet classpaths,
            Path outputDirectory,
            Path generatedSourcesDirectory,
            JavacOptions options,
            String fallbackReason,
            CompileDiagnostics diagnostics,
            boolean captureAttribution) {
        JavacResult result = javacRunner.compile(
                jdkStatus.javac().orElseThrow(),
                sources.mainSources(),
                classpaths.compile(),
                outputDirectory,
                classpaths.processor(),
                generatedSourcesDirectory,
                options,
                captureAttribution);
        return new Attempt(
                result,
                "full",
                fallbackReason,
                diagnostics,
                result.attribution(),
                sources.mainSources());
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

    private static Classpath incrementalClasspath(Classpath classpath, Path outputDirectory) {
        List<Path> entries = new ArrayList<>();
        entries.add(outputDirectory);
        entries.addAll(classpath.entries());
        return new Classpath(entries);
    }

    private static void deleteOwnedOutputs(IncrementalCompilePlan plan) {
        deleteOutputs(plan.outputsToDelete());
    }

    private static void deleteStaleOutputs(IncrementalCompilePlan plan, List<Path> sources) {
        deleteOutputs(plan.previousClassOutputs(sources));
    }

    private static void deleteOutputs(List<Path> outputs) {
        for (Path output : outputs) {
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
            CompileDiagnostics diagnostics,
            GeneratedOutputAttribution attribution,
            List<Path> compiledSources) {
        public Attempt(
                JavacResult result,
                String mode,
                String fallbackReason,
                CompileDiagnostics diagnostics) {
            this(result, mode, fallbackReason, diagnostics, GeneratedOutputAttribution.absent(), List.of());
        }

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
