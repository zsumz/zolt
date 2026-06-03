package com.zolt.build;

import com.zolt.resolve.Classpath;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;
import java.util.function.Consumer;

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
        return run(java, classpath, mainClass, arguments, ignored -> {
        });
    }

    public JavaRunResult run(
            Path java,
            Classpath classpath,
            String mainClass,
            List<String> arguments,
            Consumer<String> outputConsumer) {
        List<String> command = command(java, classpath, mainClass, arguments);
        ProcessResult result = processRunner.run(command, outputConsumer);
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

    private static ProcessResult runProcess(List<String> command, Consumer<String> outputConsumer) {
        try {
            Process process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start();
            StringBuilder output = new StringBuilder();
            byte[] buffer = new byte[8192];
            int read = process.getInputStream().read(buffer);
            while (read >= 0) {
                String chunk = new String(buffer, 0, read, StandardCharsets.UTF_8);
                output.append(chunk);
                outputConsumer.accept(chunk);
                read = process.getInputStream().read(buffer);
            }
            int exitCode = process.waitFor();
            return new ProcessResult(exitCode, output.toString());
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
        ProcessResult run(List<String> command, Consumer<String> outputConsumer);
    }

    record ProcessResult(int exitCode, String output) {
    }
}
