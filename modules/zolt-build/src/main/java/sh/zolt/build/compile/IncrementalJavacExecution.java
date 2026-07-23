package sh.zolt.build.compile;

import sh.zolt.build.BuildException;
import sh.zolt.build.incremental.GeneratedOutputAttribution;
import sh.zolt.build.incremental.IncrementalCompilePlan;
import sh.zolt.build.incremental.IncrementalCompilePlanner;
import sh.zolt.build.incremental.IncrementalCompileWaveResult;
import sh.zolt.build.incremental.IncrementalDependentCompiler;
import sh.zolt.classpath.Classpath;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Shared incremental-javac orchestration for the main and test compile executors. Both scopes drive the
 * identical isolating fast path: delete the dirty sources' previous class outputs and their attributed
 * generated outputs, recompile them (capturing processor attribution when the plan asks for it), then run
 * the ABI-driven dependent waves the same way, merging every wave's attribution and accumulating the full
 * set of recompiled sources. Keeping this in one place is what lets the test executor mirror the main
 * executor without either copying the attribution plumbing. A {@link sh.zolt.build.JavacException} from a
 * wave propagates so each scope can apply its own full-recompile fallback.
 */
public final class IncrementalJavacExecution {
    private final JavacRunner javacRunner;
    private final IncrementalCompilePlanner planner;

    public IncrementalJavacExecution(JavacRunner javacRunner, IncrementalCompilePlanner planner) {
        this.javacRunner = javacRunner;
        this.planner = planner;
    }

    public Result run(
            Path javac,
            IncrementalCompilePlan plan,
            Classpath compileClasspath,
            Path outputDirectory,
            Classpath processorClasspath,
            Path generatedSourcesDirectory,
            JavacOptions options) {
        boolean captureAttribution = plan.captureProcessorAttribution();
        List<Path> compiledSources = new ArrayList<>(plan.sourcesToCompile());
        GeneratedOutputAttribution[] attribution = {GeneratedOutputAttribution.absent()};
        deleteStaleOutputs(plan, plan.sourcesToCompile());
        deleteOutputs(plan.previousGeneratedOutputs(plan.sourcesToCompile()));
        JavacResult primary = javacRunner.compile(
                javac,
                plan.sourcesToCompile(),
                incrementalClasspath(compileClasspath, outputDirectory),
                outputDirectory,
                processorClasspath,
                generatedSourcesDirectory,
                options,
                captureAttribution);
        attribution[0] = primary.attribution();
        IncrementalCompileWaveResult waves = planner.validateAndCompileDependents(
                plan,
                dependentSources -> {
                    deleteStaleOutputs(plan, dependentSources);
                    deleteOutputs(plan.previousGeneratedOutputs(dependentSources));
                    JavacResult dependentResult = javacRunner.compile(
                            javac,
                            dependentSources,
                            incrementalClasspath(compileClasspath, outputDirectory),
                            outputDirectory,
                            processorClasspath,
                            generatedSourcesDirectory,
                            options,
                            captureAttribution);
                    attribution[0] = attribution[0].merge(dependentResult.attribution());
                    compiledSources.addAll(dependentSources);
                    return new IncrementalDependentCompiler.Outcome(
                            dependentResult.sourceCount(), dependentResult.output());
                });
        return new Result(primary, waves, attribution[0], compiledSources);
    }

    public static Classpath incrementalClasspath(Classpath classpath, Path outputDirectory) {
        List<Path> entries = new ArrayList<>();
        entries.add(outputDirectory);
        entries.addAll(classpath.entries());
        return new Classpath(entries);
    }

    public static void deleteStaleOutputs(IncrementalCompilePlan plan, List<Path> sources) {
        deleteOutputs(plan.previousClassOutputs(sources));
    }

    public static void deleteOutputs(List<Path> outputs) {
        for (Path output : outputs) {
            try {
                Files.deleteIfExists(output);
            } catch (IOException exception) {
                throw new BuildException(
                        "Could not delete stale build output "
                                + output
                                + ". Check that the build output directory is writable.",
                        exception);
            }
        }
    }

    public static String combinedOutput(String first, String second) {
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

    public record Result(
            JavacResult primary,
            IncrementalCompileWaveResult waves,
            GeneratedOutputAttribution attribution,
            List<Path> compiledSources) {
    }
}
