package com.zolt.quarkus;

import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;

public final class QuarkusBootstrapPreparer {
    public QuarkusBootstrapHandle prepare(
            QuarkusBootstrapDescriptor descriptor,
            QuarkusBootstrapApi api,
            QuarkusApplicationModelHandle applicationModel) {
        if (descriptor == null) {
            throw new QuarkusAugmentationException("Quarkus bootstrap descriptor is required.");
        }
        if (api == null) {
            throw new QuarkusAugmentationException("Quarkus bootstrap API is required.");
        }
        if (applicationModel == null) {
            throw new QuarkusAugmentationException("Quarkus application model is required.");
        }

        try {
            Class<?> bootstrapClass = Class.forName(api.bootstrapClass());
            Class<?> builderClass = Class.forName(api.builderClass());
            Object builder = bootstrapClass.getMethod("builder").invoke(null);
            builderClass.getMethod("setApplicationRoot", Path.class).invoke(builder, descriptor.applicationClasses());
            builderClass.getMethod("setProjectRoot", Path.class).invoke(builder, descriptor.projectDirectory());
            builderClass.getMethod("setTargetDirectory", Path.class).invoke(builder, descriptor.augmentationDirectory());
            existingModelSetter(builderClass, applicationModel.applicationModel().getClass())
                    .invoke(builder, applicationModel.applicationModel());
            setMode(builderClass, api.modeClass(), builder);
            Object bootstrap = builderClass.getMethod("build").invoke(builder);
            return new QuarkusBootstrapHandle(
                    bootstrap,
                    bootstrap.getClass().getName(),
                    applicationModel.applicationModelClass());
        } catch (ClassNotFoundException exception) {
            throw new QuarkusAugmentationException(
                    "Quarkus bootstrap classes are missing from the bootstrap worker classpath. "
                            + "Ensure Quarkus deployment dependencies were resolved and try again.",
                    exception);
        } catch (NoSuchMethodException exception) {
            throw new QuarkusAugmentationException(
                    "Quarkus bootstrap builder API is incompatible with Zolt. Missing method "
                            + exception.getMessage()
                            + ". Update Zolt or use a supported Quarkus version.",
                    exception);
        } catch (InstantiationException | IllegalAccessException exception) {
            throw new QuarkusAugmentationException(
                    "Could not access Quarkus bootstrap builder API. Check the Quarkus deployment classpath.",
                    exception);
        } catch (InvocationTargetException exception) {
            throw new QuarkusAugmentationException(
                    "Quarkus bootstrap preparation failed. Check the Quarkus augmentation inputs.",
                    exception.getCause() == null ? exception : exception.getCause());
        } catch (ReflectiveOperationException exception) {
            throw new QuarkusAugmentationException(
                    "Could not prepare Quarkus bootstrap. Check the Quarkus deployment classpath.",
                    exception);
        }
    }

    private static Method existingModelSetter(Class<?> builderClass, Class<?> applicationModelClass)
            throws NoSuchMethodException {
        for (Method method : builderClass.getMethods()) {
            if (!"setExistingModel".equals(method.getName()) || method.getParameterCount() != 1) {
                continue;
            }
            if (method.getParameterTypes()[0].isAssignableFrom(applicationModelClass)) {
                return method;
            }
        }
        throw new NoSuchMethodException("setExistingModel(" + applicationModelClass.getName() + ")");
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void setMode(Class<?> builderClass, String modeClassName, Object builder)
            throws ReflectiveOperationException {
        Class<?> modeClass = Class.forName(modeClassName);
        Object prod = Enum.valueOf((Class<? extends Enum>) modeClass.asSubclass(Enum.class), "PROD");
        builderClass.getMethod("setMode", modeClass).invoke(builder, prod);
    }
}
