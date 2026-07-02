package sh.zolt.quarkus.production;

import sh.zolt.quarkus.bootstrap.descriptor.QuarkusBootstrapDescriptor;
import sh.zolt.quarkus.bootstrap.QuarkusBootstrapWorkerResult;

@FunctionalInterface
public interface QuarkusAugmentor {
    QuarkusBootstrapWorkerResult augment(QuarkusAugmentationRequest request, QuarkusBootstrapDescriptor descriptor);
}
