package com.zolt.quarkus;

import java.io.PrintStream;
import java.nio.file.Path;

public final class QuarkusBootstrapWorker {
    public static final String MAIN_CLASS = "com.zolt.quarkus.QuarkusBootstrapWorker";

    private final QuarkusBootstrapDescriptorReader descriptorReader;
    private final QuarkusBootstrapApiProbe apiProbe;
    private final QuarkusApplicationModelFactory applicationModelFactory;
    private final QuarkusBootstrapPreparer bootstrapPreparer;
    private final QuarkusCuratedApplicationInvoker curatedApplicationInvoker;
    private final QuarkusProductionApplicationCreator productionApplicationCreator;
    private final QuarkusProductionApplicationSummarizer productionApplicationSummarizer;
    private final QuarkusProductionOutputValidator productionOutputValidator;
    private final PrintStream err;

    public QuarkusBootstrapWorker() {
        this(
                new QuarkusBootstrapDescriptorReader(),
                new QuarkusBootstrapApiProbe(),
                new QuarkusApplicationModelFactory(),
                new QuarkusBootstrapPreparer(),
                new QuarkusCuratedApplicationInvoker(),
                new QuarkusProductionApplicationCreator(),
                new QuarkusProductionApplicationSummarizer(),
                new QuarkusProductionOutputValidator(),
                System.err);
    }

    QuarkusBootstrapWorker(
            QuarkusBootstrapDescriptorReader descriptorReader,
            QuarkusBootstrapApiProbe apiProbe,
            QuarkusApplicationModelFactory applicationModelFactory,
            QuarkusBootstrapPreparer bootstrapPreparer,
            QuarkusCuratedApplicationInvoker curatedApplicationInvoker,
            QuarkusProductionApplicationCreator productionApplicationCreator,
            QuarkusProductionApplicationSummarizer productionApplicationSummarizer,
            QuarkusProductionOutputValidator productionOutputValidator,
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
        if (bootstrapPreparer == null) {
            throw new QuarkusAugmentationException("Quarkus bootstrap preparer is required.");
        }
        if (curatedApplicationInvoker == null) {
            throw new QuarkusAugmentationException("Quarkus curated application invoker is required.");
        }
        if (productionApplicationCreator == null) {
            throw new QuarkusAugmentationException("Quarkus production application creator is required.");
        }
        if (productionApplicationSummarizer == null) {
            throw new QuarkusAugmentationException("Quarkus production application summarizer is required.");
        }
        if (productionOutputValidator == null) {
            throw new QuarkusAugmentationException("Quarkus production output validator is required.");
        }
        if (err == null) {
            throw new QuarkusAugmentationException("Quarkus bootstrap worker error stream is required.");
        }
        this.descriptorReader = descriptorReader;
        this.apiProbe = apiProbe;
        this.applicationModelFactory = applicationModelFactory;
        this.bootstrapPreparer = bootstrapPreparer;
        this.curatedApplicationInvoker = curatedApplicationInvoker;
        this.productionApplicationCreator = productionApplicationCreator;
        this.productionApplicationSummarizer = productionApplicationSummarizer;
        this.productionOutputValidator = productionOutputValidator;
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
            QuarkusBootstrapHandle bootstrap = bootstrapPreparer.prepare(descriptor, api, applicationModel);
            QuarkusCuratedApplicationHandle curatedApplication = curatedApplicationInvoker.invoke(bootstrap);
            QuarkusProductionApplicationHandle productionApplication = productionApplicationCreator.create(curatedApplication);
            QuarkusProductionApplicationSummary productionSummary =
                    productionApplicationSummarizer.summarize(productionApplication);
            productionOutputValidator.validate(descriptor, productionSummary);
            err.println("error: Quarkus runnable package output capture is not implemented yet. "
                    + "Descriptor was accepted at "
                    + descriptor.descriptorFile()
                    + " and "
                    + bootstrap.bootstrapClass()
                    + " produced "
                    + curatedApplication.curatedApplicationClass()
                    + " with "
                    + applicationModel.applicationModelClass()
                    + " and "
                    + applicationModel.dependencyCount()
                    + " model dependencies, producing "
                    + productionApplication.augmentResultClass()
                    + " with "
                    + productionSummary.artifactResultCount()
                    + " artifact results and "
                    + jarOutput(productionSummary)
                    + ".");
            return 3;
        } catch (QuarkusAugmentationException exception) {
            err.println("error: " + exception.getMessage());
            return 1;
        }
    }

    private static String jarOutput(QuarkusProductionApplicationSummary productionSummary) {
        if (!productionSummary.hasJar()) {
            return "no jar result";
        }
        if (productionSummary.libraryDirectory() == null) {
            return "jar " + productionSummary.jarPath();
        }
        return "jar " + productionSummary.jarPath() + " using libraries at " + productionSummary.libraryDirectory();
    }
}
