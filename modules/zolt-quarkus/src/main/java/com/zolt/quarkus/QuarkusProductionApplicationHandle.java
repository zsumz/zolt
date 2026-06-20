package com.zolt.quarkus;

public record QuarkusProductionApplicationHandle(
        Object augmentResult,
        String augmentResultClass) {
    public QuarkusProductionApplicationHandle {
        if (augmentResult == null) {
            throw new QuarkusAugmentationException("Quarkus production application handle requires an augment result.");
        }
        if (augmentResultClass == null || augmentResultClass.isBlank()) {
            throw new QuarkusAugmentationException("Quarkus production application handle requires a result class.");
        }
    }
}
