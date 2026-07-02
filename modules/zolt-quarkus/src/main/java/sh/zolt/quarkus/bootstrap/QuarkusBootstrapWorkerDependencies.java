package sh.zolt.quarkus.bootstrap;

import sh.zolt.quarkus.QuarkusAugmentationException;
import sh.zolt.quarkus.bootstrap.descriptor.QuarkusBootstrapDescriptorReader;
import sh.zolt.quarkus.production.QuarkusProductionApplicationCreator;
import sh.zolt.quarkus.production.QuarkusProductionApplicationSummarizer;
import sh.zolt.quarkus.production.QuarkusProductionOutputValidator;
import sh.zolt.quarkus.production.QuarkusProductionOutputVerifier;

record QuarkusBootstrapWorkerDependencies(
        QuarkusBootstrapDescriptorReader descriptorReader,
        QuarkusBootstrapApiProbe apiProbe,
        QuarkusApplicationModelFactory applicationModelFactory,
        QuarkusBootstrapPreparer bootstrapPreparer,
        QuarkusCuratedApplicationInvoker curatedApplicationInvoker,
        QuarkusProductionApplicationCreator productionApplicationCreator,
        QuarkusProductionApplicationSummarizer productionApplicationSummarizer,
        QuarkusProductionOutputValidator productionOutputValidator,
        QuarkusProductionOutputVerifier productionOutputVerifier,
        QuarkusBootstrapWorkerResultCodec resultCodec) {
    QuarkusBootstrapWorkerDependencies {
        require(descriptorReader, "Quarkus bootstrap descriptor reader is required.");
        require(apiProbe, "Quarkus bootstrap API probe is required.");
        require(applicationModelFactory, "Quarkus application model factory is required.");
        require(bootstrapPreparer, "Quarkus bootstrap preparer is required.");
        require(curatedApplicationInvoker, "Quarkus curated application invoker is required.");
        require(productionApplicationCreator, "Quarkus production application creator is required.");
        require(productionApplicationSummarizer, "Quarkus production application summarizer is required.");
        require(productionOutputValidator, "Quarkus production output validator is required.");
        require(productionOutputVerifier, "Quarkus production output verifier is required.");
        require(resultCodec, "Quarkus bootstrap worker result codec is required.");
    }

    static QuarkusBootstrapWorkerDependencies defaults() {
        return new QuarkusBootstrapWorkerDependencies(
                new QuarkusBootstrapDescriptorReader(),
                new QuarkusBootstrapApiProbe(),
                new QuarkusApplicationModelFactory(),
                new QuarkusBootstrapPreparer(),
                new QuarkusCuratedApplicationInvoker(),
                new QuarkusProductionApplicationCreator(),
                new QuarkusProductionApplicationSummarizer(),
                new QuarkusProductionOutputValidator(),
                new QuarkusProductionOutputVerifier(),
                new QuarkusBootstrapWorkerResultCodec());
    }

    private static void require(Object value, String message) {
        if (value == null) {
            throw new QuarkusAugmentationException(message);
        }
    }
}
