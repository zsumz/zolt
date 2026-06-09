package com.zolt.quarkus;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.List;

public final class QuarkusTestWorker {
    public static final String MAIN_CLASS = "com.zolt.quarkus.QuarkusTestWorker";

    private final DescriptorReader descriptorReader;
    private final UnsupportedTestScanner unsupportedTestScanner;
    private final PrintStream out;
    private final PrintStream err;

    public QuarkusTestWorker() {
        this(
                new QuarkusTestRunnerDescriptorReader()::read,
                new QuarkusUnsupportedTestScanner()::scan,
                System.out,
                System.err);
    }

    QuarkusTestWorker(
            DescriptorReader descriptorReader,
            UnsupportedTestScanner unsupportedTestScanner,
            PrintStream out,
            PrintStream err) {
        if (descriptorReader == null) {
            throw new QuarkusAugmentationException("Quarkus test worker descriptor reader is required.");
        }
        if (unsupportedTestScanner == null) {
            throw new QuarkusAugmentationException("Quarkus test worker unsupported test scanner is required.");
        }
        if (out == null) {
            throw new QuarkusAugmentationException("Quarkus test worker output stream is required.");
        }
        if (err == null) {
            throw new QuarkusAugmentationException("Quarkus test worker error stream is required.");
        }
        this.descriptorReader = descriptorReader;
        this.unsupportedTestScanner = unsupportedTestScanner;
        this.out = out;
        this.err = err;
    }

    public static void main(String[] args) {
        int exitCode = new QuarkusTestWorker().run(args);
        System.exit(exitCode);
    }

    int run(String[] args) {
        if (args == null || args.length != 1) {
            err.println("error: Quarkus test worker requires a test runner descriptor path.");
            return 2;
        }
        try {
            QuarkusTestRunnerDescriptor descriptor = descriptorReader.read(Path.of(args[0]));
            return run(descriptor);
        } catch (QuarkusAugmentationException | QuarkusPlanException exception) {
            err.println("error: " + exception.getMessage());
            return 1;
        }
    }

    private int run(QuarkusTestRunnerDescriptor descriptor) {
        List<QuarkusUnsupportedTest> unsupportedTests = unsupportedTestScanner.scan(descriptor.testOutputDirectory());
        out.println("Quarkus test worker");
        out.println("Runner mode: " + descriptor.runnerMode());
        out.println("Descriptor: " + descriptor.descriptorFile());
        out.println("Unsupported Quarkus tests: " + unsupportedTests.size());
        for (QuarkusUnsupportedTest test : unsupportedTests) {
            out.println("  " + test.relativePath() + " (" + test.annotationName() + ")");
        }
        err.println("error: Dedicated Quarkus test worker execution is not implemented yet. "
                + "Run `zolt test` for the current plain JUnit path, or remove Quarkus-specific test annotations "
                + "until Zolt owns Quarkus JUnit discovery and launcher/session listeners.");
        return 2;
    }

    @FunctionalInterface
    interface DescriptorReader {
        QuarkusTestRunnerDescriptor read(Path descriptorFile);
    }

    @FunctionalInterface
    interface UnsupportedTestScanner {
        List<QuarkusUnsupportedTest> scan(Path testOutputDirectory);
    }
}
