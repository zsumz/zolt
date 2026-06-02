package com.zolt.build;

import com.zolt.resolve.Classpath;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

public final class JavaRunner {
    private final String pathSeparator;
    private final ProcessRunner processRunner;

    public JavaRunner() {
        this(java.io.File.pathSeparator, JavaRunner::runProcess);
    }

    JavaRunner(String pathSeparator, ProcessRunner processRunner) {
        this.pathSeparator = pathSeparator;
        this.processRunner = processRunner;
    }

    public JavaRunResult run(
            Path java,
            Classpath classpath,
            String mainClass,
            List<String> arguments) {
        List<String> command = command(java, classpath, mainClass, arguments);
        ProcessResult result = processRunner.run(command);
        if (result.exitCode() != 0) {
            throw new JavaRunException(
                    "java exited with code "
                            + result.exitCode()
                            + ". Check the application output and try again.\n"
                            + result.output().stripTrailing());
        }
        return new JavaRunResult(mainClass, result.output());
    }

    private List<String> command(
            Path java,
            Classpath classpath,
            String mainClass,
            List<String> arguments) {
        List<String> command = new ArrayList<>();
        command.add(java.toString());
        if (!classpath.entries().isEmpty()) {
            command.add("-classpath");
            StringJoiner joiner = new StringJoiner(pathSeparator);
            for (Path entry : classpath.entries()) {
                joiner.add(entry.normalize().toString());
            }
            command.add(joiner.toString());
        }
        command.add(mainClass);
        command.addAll(arguments);
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
            throw new JavaRunException(
                    "Could not run java. Check that the configured JDK is installed and readable.",
                    exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new JavaRunException("java was interrupted. Try the command again.", exception);
        }
    }

    @FunctionalInterface
    interface ProcessRunner {
        ProcessResult run(List<String> command);
    }

    record ProcessResult(int exitCode, String output) {
    }
}
