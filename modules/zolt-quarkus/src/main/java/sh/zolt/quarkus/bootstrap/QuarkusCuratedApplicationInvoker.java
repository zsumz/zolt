package sh.zolt.quarkus.bootstrap;

import sh.zolt.quarkus.QuarkusAugmentationException;
import java.lang.reflect.InvocationTargetException;

public final class QuarkusCuratedApplicationInvoker {
    public QuarkusCuratedApplicationHandle invoke(QuarkusBootstrapHandle bootstrap) {
        if (bootstrap == null) {
            throw new QuarkusAugmentationException("Quarkus bootstrap handle is required.");
        }

        try {
            Object curatedApplication = bootstrap.bootstrap().getClass().getMethod("bootstrap").invoke(bootstrap.bootstrap());
            if (curatedApplication == null) {
                throw new QuarkusAugmentationException("Quarkus bootstrap returned no curated application.");
            }
            curatedApplication.getClass().getMethod("createAugmentor");
            return new QuarkusCuratedApplicationHandle(
                    curatedApplication,
                    curatedApplication.getClass().getName());
        } catch (NoSuchMethodException exception) {
            throw new QuarkusAugmentationException(
                    "Quarkus curated application API is incompatible with Zolt. Missing method "
                            + exception.getMessage()
                            + ". Update Zolt or use a supported Quarkus version.",
                    exception);
        } catch (IllegalAccessException exception) {
            throw new QuarkusAugmentationException(
                    "Could not access Quarkus curated application API. Check the Quarkus deployment classpath.",
                    exception);
        } catch (InvocationTargetException exception) {
            throw new QuarkusAugmentationException(
                    "Quarkus bootstrap failed. Check the Quarkus augmentation inputs.",
                    exception.getCause() == null ? exception : exception.getCause());
        }
    }
}
