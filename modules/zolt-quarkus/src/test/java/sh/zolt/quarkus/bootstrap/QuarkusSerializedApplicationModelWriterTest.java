package sh.zolt.quarkus.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import sh.zolt.quarkus.QuarkusAugmentationException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

final class QuarkusSerializedApplicationModelWriterTest {
    @Test
    void delegatesSerializationToInjectedSerializer() {
        Object model = new Object();
        Path outputPath = Path.of("/repo/target/quarkus/application-model.properties");
        AtomicReference<Object> serializedModel = new AtomicReference<>();
        AtomicReference<Path> serializedPath = new AtomicReference<>();
        QuarkusSerializedApplicationModelWriter writer = new QuarkusSerializedApplicationModelWriter(
                (actualModel, actualPath) -> {
                    serializedModel.set(actualModel);
                    serializedPath.set(actualPath);
                });

        writer.write(handle(model), outputPath);

        assertSame(model, serializedModel.get());
        assertEquals(outputPath, serializedPath.get());
    }

    @Test
    void rejectsMissingSerializerAndInputsWithActionableMessages() {
        QuarkusAugmentationException missingSerializer = assertThrows(
                QuarkusAugmentationException.class,
                () -> new QuarkusSerializedApplicationModelWriter(null));
        assertTrue(missingSerializer.getMessage().contains("serializer is required"));

        QuarkusSerializedApplicationModelWriter writer = new QuarkusSerializedApplicationModelWriter(
                (model, path) -> {
                });
        QuarkusAugmentationException missingModel = assertThrows(
                QuarkusAugmentationException.class,
                () -> writer.write(null, Path.of("/tmp/model.properties")));
        assertTrue(missingModel.getMessage().contains("application model is required"));

        QuarkusAugmentationException missingOutput = assertThrows(
                QuarkusAugmentationException.class,
                () -> writer.write(handle(new Object()), null));
        assertTrue(missingOutput.getMessage().contains("output path is required"));
    }

    @Test
    void classpathFailureTellsUserToResolveAgain() {
        QuarkusSerializedApplicationModelWriter writer = new QuarkusSerializedApplicationModelWriter(
                (model, path) -> {
                    throw new ClassNotFoundException("ApplicationModelSerializer");
                });

        QuarkusAugmentationException exception = assertThrows(
                QuarkusAugmentationException.class,
                () -> writer.write(handle(new Object()), Path.of("/tmp/model.properties")));

        assertTrue(exception.getMessage().contains("serializer classes are missing from the worker classpath"));
        assertTrue(exception.getMessage().contains("Run `zolt resolve`, then run `zolt test` again"));
    }

    @Test
    void incompatibleSerializerApiTellsUserToUpdateZoltOrQuarkus() {
        QuarkusSerializedApplicationModelWriter writer = new QuarkusSerializedApplicationModelWriter(
                (model, path) -> {
                    throw new NoSuchMethodException("serialize");
                });

        QuarkusAugmentationException exception = assertThrows(
                QuarkusAugmentationException.class,
                () -> writer.write(handle(new Object()), Path.of("/tmp/model.properties")));

        assertTrue(exception.getMessage().contains("serializer API is incompatible with Zolt"));
        assertTrue(exception.getMessage().contains("Update Zolt or use a supported Quarkus version"));
    }

    @Test
    void inaccessibleSerializerMentionsDeploymentClasspath() {
        QuarkusSerializedApplicationModelWriter writer = new QuarkusSerializedApplicationModelWriter(
                (model, path) -> {
                    throw new IllegalAccessException("denied");
                });

        QuarkusAugmentationException exception = assertThrows(
                QuarkusAugmentationException.class,
                () -> writer.write(handle(new Object()), Path.of("/tmp/model.properties")));

        assertTrue(exception.getMessage().contains("Could not access Quarkus application model serializer"));
        assertTrue(exception.getMessage().contains("Check the Quarkus deployment classpath"));
    }

    @Test
    void invocationTargetExceptionUsesUnderlyingCause() {
        IllegalStateException cause = new IllegalStateException("serializer failed");
        QuarkusSerializedApplicationModelWriter writer = new QuarkusSerializedApplicationModelWriter(
                (model, path) -> {
                    throw new InvocationTargetException(cause);
                });

        QuarkusAugmentationException exception = assertThrows(
                QuarkusAugmentationException.class,
                () -> writer.write(handle(new Object()), Path.of("/tmp/model.properties")));

        assertTrue(exception.getMessage().contains("Could not serialize Quarkus application model"));
        assertSame(cause, exception.getCause());
    }

    @Test
    void genericReflectiveFailureKeepsBootstrapInputDiagnostic() {
        QuarkusSerializedApplicationModelWriter writer = new QuarkusSerializedApplicationModelWriter(
                (model, path) -> {
                    throw new InstantiationException("abstract model");
                });

        QuarkusAugmentationException exception = assertThrows(
                QuarkusAugmentationException.class,
                () -> writer.write(handle(new Object()), Path.of("/tmp/model.properties")));

        assertTrue(exception.getMessage().contains("Check the Quarkus test bootstrap inputs"));
        assertTrue(exception.getCause() instanceof InstantiationException);
    }

    private static QuarkusApplicationModelHandle handle(Object model) {
        return new QuarkusApplicationModelHandle(model, model.getClass().getName(), 0, 0, 0);
    }
}
