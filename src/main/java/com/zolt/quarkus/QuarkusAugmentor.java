package com.zolt.quarkus;

@FunctionalInterface
public interface QuarkusAugmentor {
    void augment(QuarkusAugmentationRequest request, QuarkusBootstrapDescriptor descriptor);
}
