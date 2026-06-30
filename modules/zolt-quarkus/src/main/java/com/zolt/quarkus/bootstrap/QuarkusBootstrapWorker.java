package com.zolt.quarkus.bootstrap;

import com.zolt.quarkus.QuarkusAugmentationException;
import com.zolt.quarkus.bootstrap.descriptor.QuarkusBootstrapDescriptor;
import com.zolt.quarkus.production.QuarkusProductionApplicationHandle;
import com.zolt.quarkus.production.QuarkusProductionApplicationSummary;
import java.io.PrintStream;
import java.nio.file.Path;

public final class QuarkusBootstrapWorker {
    public static final String MAIN_CLASS = "com.zolt.quarkus.bootstrap.QuarkusBootstrapWorker";

    private final QuarkusBootstrapWorkerDependencies dependencies;
    private final PrintStream out;
    private final PrintStream err;

    public QuarkusBootstrapWorker() {
        this(QuarkusBootstrapWorkerDependencies.defaults(), System.out, System.err);
    }

    QuarkusBootstrapWorker(
            QuarkusBootstrapWorkerDependencies dependencies,
            PrintStream out,
            PrintStream err) {
        if (dependencies == null) {
            throw new QuarkusAugmentationException("Quarkus bootstrap worker dependencies are required.");
        }
        if (out == null) {
            throw new QuarkusAugmentationException("Quarkus bootstrap worker output stream is required.");
        }
        if (err == null) {
            throw new QuarkusAugmentationException("Quarkus bootstrap worker error stream is required.");
        }
        this.dependencies = dependencies;
        this.out = out;
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
            QuarkusBootstrapDescriptor descriptor = dependencies.descriptorReader().read(Path.of(args[0]));
            QuarkusBootstrapApi api = dependencies.apiProbe().probe(descriptor);
            QuarkusApplicationModelHandle applicationModel = dependencies.applicationModelFactory().create(descriptor);
            QuarkusBootstrapHandle bootstrap = dependencies.bootstrapPreparer().prepare(descriptor, api, applicationModel);
            QuarkusCuratedApplicationHandle curatedApplication = dependencies.curatedApplicationInvoker().invoke(bootstrap);
            QuarkusProductionApplicationHandle productionApplication =
                    dependencies.productionApplicationCreator().create(curatedApplication);
            QuarkusProductionApplicationSummary productionSummary =
                    dependencies.productionApplicationSummarizer().summarize(productionApplication);
            dependencies.productionOutputValidator().validate(descriptor, productionSummary);
            dependencies.productionOutputVerifier().verify(descriptor, productionSummary);
            dependencies.resultCodec().write(out, result(descriptor, productionSummary));
            return 0;
        } catch (QuarkusAugmentationException exception) {
            err.println("error: " + exception.getMessage());
            if (exception.getCause() != null) {
                exception.getCause().printStackTrace(err);
            }
            return 1;
        }
    }

    private static QuarkusBootstrapWorkerResult result(
            QuarkusBootstrapDescriptor descriptor,
            QuarkusProductionApplicationSummary productionSummary) {
        return new QuarkusBootstrapWorkerResult(
                descriptor.inputFingerprint(),
                descriptor.packageDirectory(),
                productionSummary.jarPath(),
                productionSummary.libraryDirectory(),
                productionSummary.artifactResultCount());
    }
}
