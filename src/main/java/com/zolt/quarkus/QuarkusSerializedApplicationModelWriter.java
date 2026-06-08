package com.zolt.quarkus;

import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;

public final class QuarkusSerializedApplicationModelWriter {
    private final Serializer serializer;

    public QuarkusSerializedApplicationModelWriter() {
        this(QuarkusSerializedApplicationModelWriter::serializeWithQuarkusApi);
    }

    QuarkusSerializedApplicationModelWriter(Serializer serializer) {
        if (serializer == null) {
            throw new QuarkusAugmentationException("Quarkus application model serializer is required.");
        }
        this.serializer = serializer;
    }

    public void write(QuarkusApplicationModelHandle applicationModel, Path outputPath) {
        if (applicationModel == null) {
            throw new QuarkusAugmentationException("Quarkus application model is required for serialization.");
        }
        if (outputPath == null) {
            throw new QuarkusAugmentationException("Quarkus serialized application model output path is required.");
        }
        try {
            serializer.serialize(applicationModel.applicationModel(), outputPath);
        } catch (ClassNotFoundException exception) {
            throw new QuarkusAugmentationException(
                    "Quarkus application model serializer classes are missing from the worker classpath. "
                            + "Run `zolt resolve`, then run `zolt test` again.",
                    exception);
        } catch (NoSuchMethodException exception) {
            throw new QuarkusAugmentationException(
                    "Quarkus application model serializer API is incompatible with Zolt. "
                            + "Update Zolt or use a supported Quarkus version.",
                    exception);
        } catch (IllegalAccessException exception) {
            throw new QuarkusAugmentationException(
                    "Could not access Quarkus application model serializer. Check the Quarkus deployment classpath.",
                    exception);
        } catch (InvocationTargetException exception) {
            throw new QuarkusAugmentationException(
                    "Could not serialize Quarkus application model. Check the Quarkus test bootstrap inputs.",
                    exception.getCause() == null ? exception : exception.getCause());
        } catch (ReflectiveOperationException exception) {
            throw new QuarkusAugmentationException(
                    "Could not serialize Quarkus application model. Check the Quarkus test bootstrap inputs.",
                    exception);
        }
    }

    private static void serializeWithQuarkusApi(Object applicationModel, Path outputPath)
            throws ReflectiveOperationException {
        Class<?> applicationModelClass = Class.forName("io.quarkus.bootstrap.model.ApplicationModel");
        Class<?> serializerClass = Class.forName("io.quarkus.bootstrap.app.ApplicationModelSerializer");
        serializerClass
                .getMethod("serialize", applicationModelClass, Path.class)
                .invoke(null, applicationModel, outputPath);
    }

    @FunctionalInterface
    interface Serializer {
        void serialize(Object applicationModel, Path outputPath) throws ReflectiveOperationException;
    }
}
