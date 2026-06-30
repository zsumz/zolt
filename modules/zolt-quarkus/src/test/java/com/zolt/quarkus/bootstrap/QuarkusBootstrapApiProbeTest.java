package com.zolt.quarkus.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.dependency.PackageId;
import com.zolt.quarkus.QuarkusAugmentationException;
import com.zolt.quarkus.bootstrap.descriptor.QuarkusApplicationArtifact;
import com.zolt.quarkus.bootstrap.descriptor.QuarkusBootstrapDescriptor;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

final class QuarkusBootstrapApiProbeTest {
    private final QuarkusBootstrapApiProbe probe = new QuarkusBootstrapApiProbe();

    @Test
    void probesBootstrapApiSurface() {
        QuarkusBootstrapApi api = probe.probe(descriptor(
                ValidBootstrap.class.getName(),
                ValidAugmentAction.class.getName()));

        assertEquals(ValidBootstrap.class.getName(), api.bootstrapClass());
        assertEquals(ValidAugmentAction.class.getName(), api.augmentActionClass());
        assertEquals(ValidBootstrap.Builder.class.getName(), api.builderClass());
        assertEquals(ValidBootstrap.Mode.class.getName(), api.modeClass());
    }

    @Test
    void rejectsMissingBootstrapClass() {
        QuarkusAugmentationException exception = assertThrows(
                QuarkusAugmentationException.class,
                () -> probe.probe(descriptor("missing.QuarkusBootstrap", ValidAugmentAction.class.getName())));

        assertTrue(exception.getMessage().contains("worker classpath is missing"));
        assertTrue(exception.getMessage().contains("missing.QuarkusBootstrap"));
    }

    @Test
    void rejectsIncompatibleBootstrapApi() {
        QuarkusAugmentationException exception = assertThrows(
                QuarkusAugmentationException.class,
                () -> probe.probe(descriptor(IncompatibleBootstrap.class.getName(), ValidAugmentAction.class.getName())));

        assertTrue(exception.getMessage().contains("bootstrap API is incompatible"));
        assertTrue(exception.getMessage().contains("builder"));
    }

    private static QuarkusBootstrapDescriptor descriptor(String bootstrapClass, String augmentActionClass) {
        return new QuarkusBootstrapDescriptor(
                Path.of("/repo/target/quarkus/zolt-bootstrap.properties"),
                Path.of("/repo/target/quarkus/runtime-classpath.txt"),
                Path.of("/repo/target/quarkus/deployment-classpath.txt"),
                Path.of("/repo/target/quarkus/platform-properties.txt"),
                Path.of("/repo/target/quarkus/application-model.properties"),
                bootstrapClass,
                augmentActionClass,
                Path.of("/repo"),
                Path.of("/repo/target/classes"),
                Path.of("/repo/target/quarkus"),
                Path.of("/repo/target/quarkus-app"),
                "fast-jar",
                "sha256:" + "1".repeat(64),
                new QuarkusApplicationArtifact(
                        new PackageId("com.example", "demo"),
                        "1.0.0",
                        Path.of("/repo/target/classes")),
                List.of(Path.of("/cache/quarkus-rest.jar")),
                List.of(Path.of("/cache/quarkus-core-deployment.jar")),
                List.of(),
                List.of());
    }

    public static final class ValidBootstrap {
        public enum Mode {
            PROD
        }

        public static Builder builder() {
            return new Builder();
        }

        public void bootstrap() {
        }

        public static final class Builder {
            public Builder setMode(Mode ignored) {
                return this;
            }
        }
    }

    public interface ValidAugmentAction {
        Object createProductionApplication();
    }

    public static final class IncompatibleBootstrap {
        public void bootstrap() {
        }
    }
}
