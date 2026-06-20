package com.zolt.quarkus;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public final class QuarkusProductionApplicationCreator {
    public QuarkusProductionApplicationHandle create(QuarkusCuratedApplicationHandle curatedApplication) {
        if (curatedApplication == null) {
            throw new QuarkusAugmentationException("Quarkus curated application handle is required.");
        }

        try {
            Object augmentor = publicNoArgMethod(
                            curatedApplication.curatedApplication(), "createAugmentor")
                    .invoke(curatedApplication.curatedApplication());
            if (augmentor == null) {
                throw new QuarkusAugmentationException("Quarkus curated application returned no augmentor.");
            }
            Object augmentResult = publicNoArgMethod(augmentor, "createProductionApplication").invoke(augmentor);
            if (augmentResult == null) {
                throw new QuarkusAugmentationException("Quarkus production application returned no augment result.");
            }
            return new QuarkusProductionApplicationHandle(
                    augmentResult,
                    augmentResult.getClass().getName());
        } catch (NoSuchMethodException exception) {
            throw new QuarkusAugmentationException(
                    "Quarkus production application API is incompatible with Zolt. Missing method "
                            + exception.getMessage()
                            + ". Update Zolt or use a supported Quarkus version.",
                    exception);
        } catch (IllegalAccessException exception) {
            throw new QuarkusAugmentationException(
                    "Could not access Quarkus production application API. Check the Quarkus deployment classpath.",
                    exception);
        } catch (InvocationTargetException exception) {
            throw new QuarkusAugmentationException(
                    "Quarkus production application creation failed. Check the Quarkus augmentation inputs.",
                    exception.getCause() == null ? exception : exception.getCause());
        }
    }

    private static Method publicNoArgMethod(Object target, String name) throws NoSuchMethodException {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            for (Class<?> candidate : type.getInterfaces()) {
                try {
                    return candidate.getMethod(name);
                } catch (NoSuchMethodException ignored) {
                    // Keep searching the public surface before falling back to the implementation class.
                }
            }
        }
        return target.getClass().getMethod(name);
    }
}
