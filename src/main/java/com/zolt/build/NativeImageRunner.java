package com.zolt.build;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

public final class NativeImageRunner {
    private final String pathSeparator;
    private final ProcessRunner processRunner;

    public NativeImageRunner() {
        this(java.io.File.pathSeparator, NativeImageRunner::runProcess);
    }

    NativeImageRunner(String pathSeparator, ProcessRunner processRunner) {
        this.pathSeparator = pathSeparator;
        this.processRunner = processRunner;
    }

    public NativeImageResult build(NativeImageRequest request) {
        validate(request);
        createDirectories(request.outputBinary(), request.logFile());
        List<String> command = command(request);
        ProcessResult result = processRunner.run(command);
        writeLog(request.logFile(), result.output());
        if (result.exitCode() != 0) {
            throw new NativeImageException(
                    "native-image failed with exit code "
                            + result.exitCode()
                            + ". Review "
                            + request.logFile()
                            + ", fix the Native Image errors, and try again.\n"
                            + result.output().stripTrailing());
        }
        return new NativeImageResult(request.outputBinary(), request.logFile(), result.output());
    }

    private List<String> command(NativeImageRequest request) {
        List<String> command = new ArrayList<>();
        command.add(request.executable().toString());
        command.addAll(request.arguments());
        command.add("-cp");
        command.add(joinedClasspath(request));
        command.add(request.mainClass());
        command.add("-o");
        command.add(request.outputBinary().toString());
        return List.copyOf(command);
    }

    private String joinedClasspath(NativeImageRequest request) {
        StringJoiner joiner = new StringJoiner(pathSeparator);
        joiner.add(request.jarPath().normalize().toString());
        for (Path entry : request.runtimeClasspath()) {
            joiner.add(entry.normalize().toString());
        }
        return joiner.toString();
    }

    private static void validate(NativeImageRequest request) {
        if (request.jarPath() == null) {
            throw new NativeImageException("Native Image jar path is missing. Run zolt package before zolt native.");
        }
        if (request.mainClass() == null || request.mainClass().isBlank()) {
            throw new NativeImageException("Native Image main class is missing. Add [project].main to zolt.toml.");
        }
        if (request.outputBinary() == null) {
            throw new NativeImageException("Native Image output path is missing. Check [native].output and [native].imageName.");
        }
        if (request.logFile() == null) {
            throw new NativeImageException("Native Image log path is missing. Check the native output directory.");
        }
    }

    private static void createDirectories(Path outputBinary, Path logFile) {
        try {
            if (outputBinary.getParent() != null) {
                Files.createDirectories(outputBinary.getParent());
            }
            if (logFile.getParent() != null) {
                Files.createDirectories(logFile.getParent());
            }
        } catch (IOException exception) {
            throw new NativeImageException(
                    "Could not create Native Image output directories. Check that the project directory is writable.",
                    exception);
        }
    }

    private static void writeLog(Path logFile, String output) {
        try {
            Files.writeString(logFile, output, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new NativeImageException(
                    "Could not write Native Image log at "
                            + logFile
                            + ". Check that the output directory is writable.",
                    exception);
        }
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
            throw new NativeImageException(
                    "Could not run native-image. Install GraalVM Native Image or configure the native-image executable.",
                    exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new NativeImageException("native-image was interrupted. Try the native build again.", exception);
        }
    }

    @FunctionalInterface
    interface ProcessRunner {
        ProcessResult run(List<String> command);
    }

    record ProcessResult(int exitCode, String output) {
    }
}
