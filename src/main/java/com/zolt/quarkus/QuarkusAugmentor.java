package com.zolt.quarkus;

@FunctionalInterface
public interface QuarkusAugmentor {
    QuarkusBootstrapWorkerResult augment(QuarkusAugmentationRequest request, QuarkusBootstrapDescriptor descriptor);
}
