package com.zolt.quarkus.production;

import com.zolt.quarkus.QuarkusAugmentationException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Collection;

public final class QuarkusProductionApplicationSummarizer {
    public QuarkusProductionApplicationSummary summarize(QuarkusProductionApplicationHandle productionApplication) {
        if (productionApplication == null) {
            throw new QuarkusAugmentationException("Quarkus production application handle is required.");
        }

        try {
            Object augmentResult = productionApplication.augmentResult();
            int artifactResultCount = artifactResultCount(augmentResult);
            Object jarResult = publicNoArgMethod(augmentResult, "getJar").invoke(augmentResult);
            Path nativeImagePath = pathValue(augmentResult, "getNativeResult", "native image path");
            if (jarResult == null) {
                return new QuarkusProductionApplicationSummary(
                        productionApplication.augmentResultClass(),
                        artifactResultCount,
                        null,
                        null,
                        false,
                        nativeImagePath);
            }
            return new QuarkusProductionApplicationSummary(
                    productionApplication.augmentResultClass(),
                    artifactResultCount,
                    pathValue(jarResult, "getPath", "jar path"),
                    pathValue(jarResult, "getLibraryDir", "jar library directory"),
                    booleanValue(jarResult, "isUberJar"),
                    nativeImagePath);
        } catch (NoSuchMethodException exception) {
            throw new QuarkusAugmentationException(
                    "Quarkus production application result API is incompatible with Zolt. Missing method "
                            + exception.getMessage()
                            + ". Update Zolt or use a supported Quarkus version.",
                    exception);
        } catch (IllegalAccessException exception) {
            throw new QuarkusAugmentationException(
                    "Could not access Quarkus production application result API. Check the Quarkus deployment classpath.",
                    exception);
        } catch (InvocationTargetException exception) {
            throw new QuarkusAugmentationException(
                    "Quarkus production application result inspection failed. Check the Quarkus augmentation output.",
                    exception.getCause() == null ? exception : exception.getCause());
        }
    }

    private static int artifactResultCount(Object augmentResult)
            throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        Object results = publicNoArgMethod(augmentResult, "getResults").invoke(augmentResult);
        if (results == null) {
            return 0;
        }
        if (results instanceof Collection<?> collection) {
            return collection.size();
        }
        throw new QuarkusAugmentationException(
                "Quarkus production application result API returned non-collection results from getResults(). "
                        + "Update Zolt or use a supported Quarkus version.");
    }

    private static Path pathValue(Object target, String methodName, String label)
            throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        Object value = publicNoArgMethod(target, methodName).invoke(target);
        if (value == null) {
            return null;
        }
        if (value instanceof Path path) {
            return path;
        }
        throw new QuarkusAugmentationException(
                "Quarkus production application result API returned a non-path " + label + ". "
                        + "Update Zolt or use a supported Quarkus version.");
    }

    private static boolean booleanValue(Object target, String methodName)
            throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        Object value = publicNoArgMethod(target, methodName).invoke(target);
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        throw new QuarkusAugmentationException(
                "Quarkus production application result API returned a non-boolean value from "
                        + methodName
                        + "(). Update Zolt or use a supported Quarkus version.");
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
