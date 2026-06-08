package com.zolt.quarkus;

import java.io.PrintStream;
import java.nio.file.Path;

public final class QuarkusBootstrapWorker {
    public static final String MAIN_CLASS = "com.zolt.quarkus.QuarkusBootstrapWorker";

    private final QuarkusBootstrapDescriptorReader descriptorReader;
    private final QuarkusBootstrapApiProbe apiProbe;
    private final QuarkusApplicationModelFactory applicationModelFactory;
    private final PrintStream err;

    public QuarkusBootstrapWorker() {
        this(
                new QuarkusBootstrapDescriptorReader(),
                new QuarkusBootstrapApiProbe(),
                new QuarkusApplicationModelFactory(),
                System.err);
    }

    QuarkusBootstrapWorker(
            QuarkusBootstrapDescriptorReader descriptorReader,
            QuarkusBootstrapApiProbe apiProbe,
            QuarkusApplicationModelFactory applicationModelFactory,
            PrintStream err) {
        if (descriptorReader == null) {
            throw new QuarkusAugmentationException("Quarkus bootstrap descriptor reader is required.");
        }
        if (apiProbe == null) {
            throw new QuarkusAugmentationException("Quarkus bootstrap API probe is required.");
        }
        if (applicationModelFactory == null) {
            throw new QuarkusAugmentationException("Quarkus application model factory is required.");
        }
        if (err == null) {
            throw new QuarkusAugmentationException("Quarkus bootstrap worker error stream is required.");
        }
        this.descriptorReader = descriptorReader;
        this.apiProbe = apiProbe;
        this.applicationModelFactory = applicationModelFactory;
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
            QuarkusApplicationModelHandle applicationModel = applicationModelFactory.create(descriptor);
            err.println("error: Quarkus augmentation invocation is not implemented yet. "
                    + "Descriptor was accepted at "
                    + descriptor.descriptorFile()
                    + " and "
                    + api.bootstrapClass()
                    + " was found. "
                    + applicationModel.applicationModelClass()
                    + " was built with "
                    + applicationModel.dependencyCount()
                    + " model dependencies."
                    + ".");
            return 3;
        } catch (QuarkusAugmentationException exception) {
            err.println("error: " + exception.getMessage());
            return 1;
        }
    }
}
