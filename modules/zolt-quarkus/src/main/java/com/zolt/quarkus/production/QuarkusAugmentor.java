package com.zolt.quarkus.production;

import com.zolt.quarkus.bootstrap.descriptor.QuarkusBootstrapDescriptor;
import com.zolt.quarkus.bootstrap.QuarkusBootstrapWorkerResult;

@FunctionalInterface
public interface QuarkusAugmentor {
    QuarkusBootstrapWorkerResult augment(QuarkusAugmentationRequest request, QuarkusBootstrapDescriptor descriptor);
}
