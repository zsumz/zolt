package com.zolt.quarkus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.resolve.PackageId;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

final class QuarkusBootstrapPreparerTest {
    private final QuarkusBootstrapPreparer preparer = new QuarkusBootstrapPreparer();

    @Test
    void preparesBootstrapWithApplicationModelAndOutputPaths() {
        FakeApplicationModel model = new FakeApplicationModel();
        QuarkusApplicationModelHandle applicationModel = new QuarkusApplicationModelHandle(
                model,
                model.getClass().getName(),
                2,
                1,
                1);

        QuarkusBootstrapHandle handle = preparer.prepare(descriptor(), api(), applicationModel);

        assertEquals(FakeBootstrap.class.getName(), handle.bootstrapClass());
        assertEquals(FakeApplicationModel.class.getName(), handle.applicationModelClass());
        FakeBootstrap bootstrap = (FakeBootstrap) handle.bootstrap();
        assertEquals(Path.of("/repo/target/classes"), bootstrap.applicationRoot());
        assertEquals(Path.of("/repo"), bootstrap.projectRoot());
        assertEquals(Path.of("/repo/target/quarkus"), bootstrap.targetDirectory());
        assertEquals(FakeBootstrap.Mode.PROD, bootstrap.mode());
        assertSame(model, bootstrap.existingModel());
    }

    @Test
    void rejectsMissingBootstrapClasses() {
        QuarkusBootstrapApi api = new QuarkusBootstrapApi(
                "missing.QuarkusBootstrap",
                "missing.AugmentAction",
                FakeBootstrap.Builder.class.getName(),
                FakeBootstrap.Mode.class.getName());

        QuarkusAugmentationException exception = assertThrows(
                QuarkusAugmentationException.class,
                () -> preparer.prepare(descriptor(), api, applicationModel()));

        assertTrue(exception.getMessage().contains("bootstrap classes are missing"));
    }

    @Test
    void rejectsIncompatibleBuilderApi() {
        QuarkusBootstrapApi api = new QuarkusBootstrapApi(
                IncompatibleBootstrap.class.getName(),
                FakeAugmentAction.class.getName(),
                IncompatibleBootstrap.Builder.class.getName(),
                IncompatibleBootstrap.Mode.class.getName());

        QuarkusAugmentationException exception = assertThrows(
                QuarkusAugmentationException.class,
                () -> preparer.prepare(descriptor(), api, applicationModel()));

        assertTrue(exception.getMessage().contains("bootstrap builder API is incompatible"));
        assertTrue(exception.getMessage().contains("setExistingModel"));
    }

    private static QuarkusApplicationModelHandle applicationModel() {
        FakeApplicationModel model = new FakeApplicationModel();
        return new QuarkusApplicationModelHandle(model, model.getClass().getName(), 0, 0, 0);
    }

    private static QuarkusBootstrapApi api() {
        return new QuarkusBootstrapApi(
                FakeBootstrap.class.getName(),
                FakeAugmentAction.class.getName(),
                FakeBootstrap.Builder.class.getName(),
                FakeBootstrap.Mode.class.getName());
    }

    private static QuarkusBootstrapDescriptor descriptor() {
        return new QuarkusBootstrapDescriptor(
                Path.of("/repo/target/quarkus/zolt-bootstrap.properties"),
                Path.of("/repo/target/quarkus/runtime-classpath.txt"),
                Path.of("/repo/target/quarkus/deployment-classpath.txt"),
                Path.of("/repo/target/quarkus/application-model.properties"),
                FakeBootstrap.class.getName(),
                FakeAugmentAction.class.getName(),
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
                List.of(),
                List.of(),
                List.of());
    }

    public static final class FakeBootstrap {
        public enum Mode {
            PROD
        }

        private final Path applicationRoot;
        private final Path projectRoot;
        private final Path targetDirectory;
        private final Mode mode;
        private final FakeApplicationModel existingModel;

        private FakeBootstrap(Builder builder) {
            this.applicationRoot = builder.applicationRoot;
            this.projectRoot = builder.projectRoot;
            this.targetDirectory = builder.targetDirectory;
            this.mode = builder.mode;
            this.existingModel = builder.existingModel;
        }

        public static Builder builder() {
            return new Builder();
        }

        public void bootstrap() {
        }

        Path applicationRoot() {
            return applicationRoot;
        }

        Path projectRoot() {
            return projectRoot;
        }

        Path targetDirectory() {
            return targetDirectory;
        }

        Mode mode() {
            return mode;
        }

        FakeApplicationModel existingModel() {
            return existingModel;
        }

        public static final class Builder {
            private Path applicationRoot;
            private Path projectRoot;
            private Path targetDirectory;
            private Mode mode;
            private FakeApplicationModel existingModel;

            public Builder setApplicationRoot(Path applicationRoot) {
                this.applicationRoot = applicationRoot;
                return this;
            }

            public Builder setProjectRoot(Path projectRoot) {
                this.projectRoot = projectRoot;
                return this;
            }

            public Builder setTargetDirectory(Path targetDirectory) {
                this.targetDirectory = targetDirectory;
                return this;
            }

            public Builder setMode(Mode mode) {
                this.mode = mode;
                return this;
            }

            public Builder setExistingModel(FakeApplicationModel existingModel) {
                this.existingModel = existingModel;
                return this;
            }

            public FakeBootstrap build() {
                return new FakeBootstrap(this);
            }
        }
    }

    public interface FakeAugmentAction {
        Object createProductionApplication();
    }

    public static final class FakeApplicationModel {
    }

    public static final class IncompatibleBootstrap {
        public enum Mode {
            PROD
        }

        public static Builder builder() {
            return new Builder();
        }

        public void bootstrap() {
        }

        public static final class Builder {
            public Builder setApplicationRoot(Path applicationRoot) {
                return this;
            }

            public Builder setProjectRoot(Path projectRoot) {
                return this;
            }

            public Builder setTargetDirectory(Path targetDirectory) {
                return this;
            }

            public Builder setMode(Mode mode) {
                return this;
            }

            public FakeBootstrap build() {
                return null;
            }
        }
    }
}
