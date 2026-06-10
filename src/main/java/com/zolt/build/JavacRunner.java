package com.zolt.build;

import com.zolt.resolve.Classpath;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.StringJoiner;

public final class JavacRunner {
    private final String pathSeparator;
    private final ProcessRunner processRunner;

    public JavacRunner() {
        this(java.io.File.pathSeparator, JavacRunner::runProcess);
    }

    JavacRunner(String pathSeparator, ProcessRunner processRunner) {
        this.pathSeparator = pathSeparator;
        this.processRunner = processRunner;
    }

    public JavacResult compile(
            Path javac,
            List<Path> sources,
            Classpath classpath,
            Path outputDirectory) {
        return compile(javac, sources, classpath, outputDirectory, new Classpath(List.of()), null);
    }

    public JavacResult compile(
            Path javac,
            List<Path> sources,
            Classpath classpath,
            Path outputDirectory,
            Classpath processorClasspath,
            Path generatedSourcesDirectory) {
        return compile(
                javac,
                sources,
                classpath,
                outputDirectory,
                processorClasspath,
                generatedSourcesDirectory,
                JavacOptions.empty());
    }

    public JavacResult compile(
            Path javac,
            List<Path> sources,
            Classpath classpath,
            Path outputDirectory,
            Classpath processorClasspath,
            Path generatedSourcesDirectory,
            JavacOptions options) {
        JavacOptions effectiveOptions = options == null ? JavacOptions.empty() : options;
        List<Path> sortedSources = sources.stream().map(Path::normalize).sorted().toList();
        Path effectiveGeneratedSourcesDirectory = sortedEntries(processorClasspath).isEmpty()
                ? null
                : generatedSourcesDirectory;
        try {
            Files.createDirectories(outputDirectory);
            if (effectiveGeneratedSourcesDirectory != null) {
                Files.createDirectories(effectiveGeneratedSourcesDirectory);
            }
        } catch (IOException exception) {
            throw new JavacException(
                    "Could not create compilation output directories for "
                            + outputDirectory
                            + ". Check that the project directory is writable.",
                    exception);
        }
        if (sortedSources.isEmpty()) {
            return new JavacResult(0, outputDirectory, "");
        }

        List<String> command = command(
                javac,
                sortedSources,
                classpath,
                outputDirectory,
                processorClasspath,
                effectiveGeneratedSourcesDirectory,
                effectiveOptions);
        ProcessResult result = processRunner.run(command);
        if (result.exitCode() != 0) {
            throw new JavacException(
                    "javac failed with exit code "
                            + result.exitCode()
                            + ". Fix the Java compilation errors and try again. "
                            + "If annotation processing is configured, inspect [annotationProcessors], "
                            + "[test.annotationProcessors], and processor-scoped entries in zolt.lock.\n"
                            + result.output().stripTrailing());
        }
        return new JavacResult(sortedSources.size(), outputDirectory, result.output());
    }

    private List<String> command(
            Path javac,
            List<Path> sources,
            Classpath classpath,
            Path outputDirectory,
            Classpath processorClasspath,
            Path generatedSourcesDirectory,
            JavacOptions options) {
        List<String> command = new ArrayList<>();
        command.add(javac.toString());
        command.add("-d");
        command.add(outputDirectory.toString());
        if (!options.release().isBlank()) {
            command.add("--release");
            command.add(options.release());
        }
        if (!options.encoding().isBlank()) {
            command.add("-encoding");
            command.add(options.encoding());
        }
        List<Path> classpathEntries = sortedEntries(classpath);
        if (!classpathEntries.isEmpty()) {
            command.add("-classpath");
            command.add(joinedPath(classpathEntries));
        }
        List<Path> processorClasspathEntries = sortedEntries(processorClasspath);
        if (processorClasspathEntries.isEmpty()) {
            command.add("-proc:none");
        } else {
            command.add("-processorpath");
            command.add(joinedPath(combinedProcessorPath(processorClasspathEntries, classpathEntries)));
        }
        if (generatedSourcesDirectory != null) {
            command.add("-s");
            command.add(generatedSourcesDirectory.toString());
        }
        command.addAll(options.arguments());
        for (Path source : sources) {
            command.add(source.toString());
        }
        return List.copyOf(command);
    }

    private static List<Path> sortedEntries(Classpath classpath) {
        if (classpath == null) {
            return List.of();
        }
        return classpath.entries().stream()
                .map(Path::normalize)
                .sorted(Comparator.naturalOrder())
                .toList();
    }

    private String joinedPath(List<Path> entries) {
        StringJoiner joiner = new StringJoiner(pathSeparator);
        for (Path entry : entries) {
            joiner.add(entry.toString());
        }
        return joiner.toString();
    }

    private static List<Path> combinedProcessorPath(List<Path> processorEntries, List<Path> classpathEntries) {
        LinkedHashSet<Path> entries = new LinkedHashSet<>();
        entries.addAll(processorEntries);
        entries.addAll(classpathEntries);
        return List.copyOf(entries);
    }

    private static ProcessResult runProcess(List<String> command) {
        try {
            Process process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int exitCode = process.waitFor();
            return new ProcessResult(exitCode, output);
        } catch (IOException exception) {
            throw new JavacException(
                    "Could not run javac. Check that the configured JDK is installed and readable.",
                    exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new JavacException("javac was interrupted. Try the build again.", exception);
        }
    }

    @FunctionalInterface
    interface ProcessRunner {
        ProcessResult run(List<String> command);
    }

    record ProcessResult(int exitCode, String output) {
    }
}
