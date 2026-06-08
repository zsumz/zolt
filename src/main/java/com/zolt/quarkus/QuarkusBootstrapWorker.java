package com.zolt.quarkus;

import java.io.PrintStream;
import java.nio.file.Path;

public final class QuarkusBootstrapWorker {
    public static final String MAIN_CLASS = "com.zolt.quarkus.QuarkusBootstrapWorker";

    private final QuarkusBootstrapDescriptorReader descriptorReader;
    private final QuarkusBootstrapApiProbe apiProbe;
    private final PrintStream err;

    public QuarkusBootstrapWorker() {
        this(new QuarkusBootstrapDescriptorReader(), new QuarkusBootstrapApiProbe(), System.err);
    }

    QuarkusBootstrapWorker(
            QuarkusBootstrapDescriptorReader descriptorReader,
            QuarkusBootstrapApiProbe apiProbe,
            PrintStream err) {
        if (descriptorReader == null) {
            throw new QuarkusAugmentationException("Quarkus bootstrap descriptor reader is required.");
        }
        if (apiProbe == null) {
            throw new QuarkusAugmentationException("Quarkus bootstrap API probe is required.");
        }
        if (err == null) {
            throw new QuarkusAugmentationException("Quarkus bootstrap worker error stream is required.");
        }
        this.descriptorReader = descriptorReader;
        this.apiProbe = apiProbe;
        this.err = err;
    }

    public static void main(String[] args) {
        int exitCode = new QuarkusBootstrapWorker().run(args);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    int run(String[] args) {
        if (args == null || args.length != 1) {
            err.println("error: Quarkus bootstrap worker requires exactly one descriptor path argument.");
            return 2;
        }

        try {
            QuarkusBootstrapDescriptor descriptor = descriptorReader.read(Path.of(args[0]));
            QuarkusBootstrapApi api = apiProbe.probe(descriptor);
            err.println("error: Quarkus bootstrap ApplicationModel invocation is not implemented yet. "
                    + "Descriptor was accepted at "
                    + descriptor.descriptorFile()
                    + " and "
                    + api.bootstrapClass()
                    + " was found with "
                    + descriptor.bootstrapDependencies().size()
                    + " model dependencies."
                    + ".");
            return 3;
        } catch (QuarkusAugmentationException exception) {
            err.println("error: " + exception.getMessage());
            return 1;
        }
    }
}
