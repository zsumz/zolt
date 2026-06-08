package com.zolt.quarkus;

import java.lang.reflect.InvocationTargetException;

public final class QuarkusBootstrapApiProbe {
    public QuarkusBootstrapApi probe(QuarkusBootstrapDescriptor descriptor) {
        if (descriptor == null) {
            throw new QuarkusAugmentationException("Quarkus bootstrap descriptor is required.");
        }

        try {
            Class<?> bootstrapClass = Class.forName(descriptor.bootstrapClass());
            Class<?> augmentActionClass = Class.forName(descriptor.augmentActionClass());
            Object builder = bootstrapClass.getMethod("builder").invoke(null);
            bootstrapClass.getMethod("bootstrap");
            augmentActionClass.getMethod("createProductionApplication");
            return new QuarkusBootstrapApi(bootstrapClass.getName(), augmentActionClass.getName(), builder.getClass().getName());
        } catch (ClassNotFoundException exception) {
            throw new QuarkusAugmentationException(
                    "Quarkus bootstrap worker classpath is missing "
                            + exception.getMessage()
                            + ". Ensure quarkus-deployment dependencies were resolved and try again.",
                    exception);
        } catch (NoSuchMethodException exception) {
            throw new QuarkusAugmentationException(
                    "Quarkus bootstrap API is incompatible with Zolt. Missing method "
                            + exception.getMessage()
                            + ". Update Zolt or use a supported Quarkus version.",
                    exception);
        } catch (IllegalAccessException exception) {
            throw new QuarkusAugmentationException(
                    "Could not access Quarkus bootstrap API. Check the Quarkus deployment classpath.",
                    exception);
        } catch (InvocationTargetException exception) {
            throw new QuarkusAugmentationException(
                    "Quarkus bootstrap builder failed. Check the Quarkus deployment classpath.",
                    exception.getCause() == null ? exception : exception.getCause());
        }
    }
}
