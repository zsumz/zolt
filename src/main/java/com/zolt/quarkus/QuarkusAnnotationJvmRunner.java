package com.zolt.quarkus;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

public final class QuarkusAnnotationJvmRunner {
    private final String pathSeparator;
    private final Path javaExecutable;
    private final ProcessRunner processRunner;

    public QuarkusAnnotationJvmRunner() {
        this(java.io.File.pathSeparator, defaultJavaExecutable(), QuarkusAnnotationJvmRunner::runProcess);
    }

    QuarkusAnnotationJvmRunner(
            String pathSeparator,
            Path javaExecutable,
            ProcessRunner processRunner) {
        if (pathSeparator == null || pathSeparator.isBlank()) {
            throw new QuarkusAugmentationException("Quarkus annotation JVM runner path separator is required.");
        }
        if (javaExecutable == null) {
            throw new QuarkusAugmentationException("Quarkus annotation JVM runner Java executable is required.");
        }
        if (processRunner == null) {
            throw new QuarkusAugmentationException("Quarkus annotation JVM runner process runner is required.");
        }
        this.pathSeparator = pathSeparator;
        this.javaExecutable = javaExecutable;
        this.processRunner = processRunner;
    }

    public Result run(QuarkusAnnotationLaunchRequest request) {
        if (request == null) {
            throw new QuarkusAugmentationException("Quarkus annotation launch request is required.");
        }
        return processRunner.run(command(request));
    }

    List<String> command(QuarkusAnnotationLaunchRequest request) {
        if (request == null) {
            throw new QuarkusAugmentationException("Quarkus annotation launch request is required.");
        }
        List<String> command = new ArrayList<>();
        command.add(javaExecutable.toString());
        command.addAll(request.jvmArguments());
        command.add("-classpath");
        command.add(joined(request.launcherClasspath()));
        command.addAll(request.consoleArguments());
        return List.copyOf(command);
    }

    private String joined(List<Path> classpath) {
        StringJoiner joiner = new StringJoiner(pathSeparator);
        for (Path entry : classpath) {
            joiner.add(entry.normalize().toString());
        }
        return joiner.toString();
    }

    private static Path defaultJavaExecutable() {
        return Path.of(System.getProperty("java.home"), "bin", executableName());
    }

    private static String executableName() {
        return System.getProperty("os.name", "").toLowerCase().contains("win") ? "java.exe" : "java";
    }

    private static Result runProcess(List<String> command) {
        try {
            Process process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int exitCode = process.waitFor();
            return new Result(exitCode, output);
        } catch (IOException exception) {
            throw new QuarkusAugmentationException(
                    "Could not run Quarkus annotation JVM runner. "
                            + "Check that the configured JDK is installed and readable.",
                    exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new QuarkusAugmentationException(
                    "Quarkus annotation JVM runner was interrupted. Try again.",
                    exception);
        }
    }

    @FunctionalInterface
    interface ProcessRunner {
        Result run(List<String> command);
    }

    public record Result(int exitCode, String output) {
    }
}
