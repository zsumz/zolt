package com.zolt.quarkus.bootstrap;

import com.zolt.quarkus.QuarkusAugmentationException;

public record QuarkusCuratedApplicationHandle(
        Object curatedApplication,
        String curatedApplicationClass) {
    public QuarkusCuratedApplicationHandle {
        if (curatedApplication == null) {
            throw new QuarkusAugmentationException("Quarkus curated application handle requires an instance.");
        }
        if (curatedApplicationClass == null || curatedApplicationClass.isBlank()) {
            throw new QuarkusAugmentationException("Quarkus curated application handle requires a class.");
        }
    }
}
