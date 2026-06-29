package com.zolt.quarkus.bootstrap;

import com.zolt.quarkus.QuarkusAugmentationException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class QuarkusBootstrapWorkerResultCodec {
    static final String BEGIN = "zolt.quarkus.worker.result.begin";
    static final String END = "zolt.quarkus.worker.result.end";
    private static final String VERSION = "1";

    public void write(PrintStream out, QuarkusBootstrapWorkerResult result) {
        if (out == null) {
            throw new QuarkusAugmentationException("Quarkus bootstrap worker result output stream is required.");
        }
        if (result == null) {
            throw new QuarkusAugmentationException("Quarkus bootstrap worker result is required.");
        }
        out.println(BEGIN);
        out.println("version=" + VERSION);
        out.println("inputFingerprint=" + result.inputFingerprint());
        out.println("packageDirectory=" + result.packageDirectory());
        out.println("runnerJar=" + result.runnerJar());
        out.println("libraryDirectory=" + (result.libraryDirectory() == null ? "" : result.libraryDirectory()));
        out.println("artifactResultCount=" + result.artifactResultCount());
        out.println(END);
    }

    public Optional<QuarkusBootstrapWorkerResult> parse(String output) {
        if (output == null || output.isBlank()) {
            return Optional.empty();
        }
        Map<String, String> values = new LinkedHashMap<>();
        boolean inResult = false;
        for (String line : output.lines().toList()) {
            if (BEGIN.equals(line)) {
                values.clear();
                inResult = true;
                continue;
            }
            if (END.equals(line) && inResult) {
                return Optional.of(result(values));
            }
            if (inResult) {
                int separator = line.indexOf('=');
                if (separator <= 0) {
                    throw new QuarkusAugmentationException(
                            "Quarkus bootstrap worker result contains malformed line: " + line);
                }
                values.put(line.substring(0, separator), line.substring(separator + 1));
            }
        }
        if (inResult) {
            throw new QuarkusAugmentationException("Quarkus bootstrap worker result was not terminated.");
        }
        return Optional.empty();
    }

    private static QuarkusBootstrapWorkerResult result(Map<String, String> values) {
        String version = required(values, "version");
        if (!VERSION.equals(version)) {
            throw new QuarkusAugmentationException(
                    "Quarkus bootstrap worker result version " + version + " is not supported.");
        }
        String libraryDirectory = values.getOrDefault("libraryDirectory", "");
        return new QuarkusBootstrapWorkerResult(
                required(values, "inputFingerprint"),
                Path.of(required(values, "packageDirectory")),
                Path.of(required(values, "runnerJar")),
                libraryDirectory.isBlank() ? null : Path.of(libraryDirectory),
                artifactResultCount(required(values, "artifactResultCount")));
    }

    private static String required(Map<String, String> values, String key) {
        String value = values.get(key);
        if (value == null || value.isBlank()) {
            throw new QuarkusAugmentationException(
                    "Quarkus bootstrap worker result is missing required field " + key + ".");
        }
        return value;
    }

    private static int artifactResultCount(String value) {
        try {
            int count = Integer.parseInt(value);
            if (count < 0) {
                throw new NumberFormatException("negative");
            }
            return count;
        } catch (NumberFormatException exception) {
            throw new QuarkusAugmentationException(
                    "Quarkus bootstrap worker result has invalid artifactResultCount " + value + ".",
                    exception);
        }
    }
}
