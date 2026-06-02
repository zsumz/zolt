package com.zolt.build;

import com.zolt.resolve.Classpath;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
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
        List<Path> sortedSources = sources.stream().map(Path::normalize).sorted().toList();
        try {
            Files.createDirectories(outputDirectory);
        } catch (IOException exception) {
            throw new JavacException(
                    "Could not create compilation output directory "
                            + outputDirectory
                            + ". Check that the project directory is writable.",
                    exception);
        }
        if (sortedSources.isEmpty()) {
            return new JavacResult(0, outputDirectory, "");
        }

        List<String> command = command(javac, sortedSources, classpath, outputDirectory);
        ProcessResult result = processRunner.run(command);
        if (result.exitCode() != 0) {
            throw new JavacException(
                    "javac failed with exit code "
                            + result.exitCode()
                            + ". Fix the Java compilation errors and try again.\n"
                            + result.output().stripTrailing());
        }
        return new JavacResult(sortedSources.size(), outputDirectory, result.output());
    }

    private List<String> command(
            Path javac,
            List<Path> sources,
            Classpath classpath,
            Path outputDirectory) {
        List<String> command = new ArrayList<>();
        command.add(javac.toString());
        command.add("-d");
        command.add(outputDirectory.toString());
        List<Path> classpathEntries = classpath.entries().stream()
                .map(Path::normalize)
                .sorted(Comparator.naturalOrder())
                .toList();
        if (!classpathEntries.isEmpty()) {
            command.add("-classpath");
            StringJoiner joiner = new StringJoiner(pathSeparator);
            for (Path entry : classpathEntries) {
                joiner.add(entry.toString());
            }
            command.add(joiner.toString());
        }
        for (Path source : sources) {
            command.add(source.toString());
        }
        return List.copyOf(command);
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
