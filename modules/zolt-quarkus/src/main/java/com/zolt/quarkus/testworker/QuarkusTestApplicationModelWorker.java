package com.zolt.quarkus.testworker;

import com.zolt.quarkus.QuarkusAugmentationException;
import com.zolt.quarkus.bootstrap.QuarkusApplicationModelFactory;
import com.zolt.quarkus.bootstrap.QuarkusApplicationModelHandle;
import com.zolt.quarkus.bootstrap.QuarkusApplicationModelOptions;
import com.zolt.quarkus.bootstrap.descriptor.QuarkusBootstrapDescriptor;
import com.zolt.quarkus.bootstrap.descriptor.QuarkusBootstrapDescriptorReader;
import com.zolt.quarkus.bootstrap.QuarkusSerializedApplicationModelWriter;
import com.zolt.quarkus.bootstrap.QuarkusWorkspaceModuleInputs;
import java.io.PrintStream;
import java.nio.file.Path;

public final class QuarkusTestApplicationModelWorker {
    public static final String MAIN_CLASS = "com.zolt.quarkus.testworker.QuarkusTestApplicationModelWorker";

    private final QuarkusBootstrapDescriptorReader descriptorReader;
    private final QuarkusApplicationModelFactory applicationModelFactory;
    private final QuarkusSerializedApplicationModelWriter modelWriter;
    private final PrintStream out;
    private final PrintStream err;

    public QuarkusTestApplicationModelWorker() {
        this(
                new QuarkusBootstrapDescriptorReader(),
                new QuarkusApplicationModelFactory(),
                new QuarkusSerializedApplicationModelWriter(),
                System.out,
                System.err);
    }

    QuarkusTestApplicationModelWorker(
            QuarkusBootstrapDescriptorReader descriptorReader,
            QuarkusApplicationModelFactory applicationModelFactory,
            QuarkusSerializedApplicationModelWriter modelWriter,
            PrintStream out,
            PrintStream err) {
        if (descriptorReader == null) {
            throw new QuarkusAugmentationException("Quarkus bootstrap descriptor reader is required.");
        }
        if (applicationModelFactory == null) {
            throw new QuarkusAugmentationException("Quarkus application model factory is required.");
        }
        if (modelWriter == null) {
            throw new QuarkusAugmentationException("Quarkus serialized application model writer is required.");
        }
        if (out == null) {
            throw new QuarkusAugmentationException("Quarkus test application model worker output stream is required.");
        }
        if (err == null) {
            throw new QuarkusAugmentationException("Quarkus test application model worker error stream is required.");
        }
        this.descriptorReader = descriptorReader;
        this.applicationModelFactory = applicationModelFactory;
        this.modelWriter = modelWriter;
        this.out = out;
        this.err = err;
    }

    public static void main(String[] args) {
        int exitCode = new QuarkusTestApplicationModelWorker().run(args);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    int run(String[] args) {
        if (args == null || args.length != 10) {
            err.println("error: Quarkus test application model worker requires descriptor, output path, and workspace module arguments.");
            return 2;
        }

        try {
            QuarkusBootstrapDescriptor descriptor = descriptorReader.read(Path.of(args[0]));
            QuarkusApplicationModelHandle applicationModel =
                    applicationModelFactory.create(
                            descriptor,
                            java.util.Optional.of(workspaceModuleInputs(args)),
                            QuarkusApplicationModelOptions.TEST_BOOTSTRAP);
            Path outputPath = Path.of(args[1]).toAbsolutePath().normalize();
            modelWriter.write(applicationModel, outputPath);
            out.println("zolt.quarkus.test-app-model=" + outputPath);
            return 0;
        } catch (QuarkusAugmentationException exception) {
            err.println("error: " + exception.getMessage());
            if (exception.getCause() != null) {
                exception.getCause().printStackTrace(err);
            }
            return 1;
        }
    }

    private static QuarkusWorkspaceModuleInputs workspaceModuleInputs(String[] args) {
        return new QuarkusWorkspaceModuleInputs(
                Path.of(args[2]),
                Path.of(args[3]),
                Path.of(args[4]),
                Path.of(args[5]),
                Path.of(args[6]),
                Path.of(args[7]),
                Path.of(args[8]),
                Path.of(args[9]));
    }
}
