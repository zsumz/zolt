package sh.zolt.quarkus.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import sh.zolt.dependency.PackageId;
import sh.zolt.quarkus.QuarkusAugmentationException;
import sh.zolt.quarkus.bootstrap.descriptor.QuarkusApplicationArtifact;
import sh.zolt.quarkus.bootstrap.descriptor.QuarkusBootstrapDescriptor;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

final class QuarkusBootstrapPreparerTest {
    private final QuarkusBootstrapPreparer preparer = new QuarkusBootstrapPreparer();

    @Test
    void preparesBootstrapWithApplicationModelAndOutputPaths() {
        QuarkusBootstrapPreparerTestDoubles.FakeApplicationModel model =
                new QuarkusBootstrapPreparerTestDoubles.FakeApplicationModel();
        QuarkusApplicationModelHandle applicationModel = new QuarkusApplicationModelHandle(
                model,
                model.getClass().getName(),
                2,
                1,
                1);

        QuarkusBootstrapHandle handle = preparer.prepare(descriptor(), api(), applicationModel);

        assertEquals(QuarkusBootstrapPreparerTestDoubles.FakeBootstrap.class.getName(), handle.bootstrapClass());
        assertEquals(QuarkusBootstrapPreparerTestDoubles.FakeApplicationModel.class.getName(), handle.applicationModelClass());
        QuarkusBootstrapPreparerTestDoubles.FakeBootstrap bootstrap =
                (QuarkusBootstrapPreparerTestDoubles.FakeBootstrap) handle.bootstrap();
        assertEquals(Path.of("/repo/target/classes"), bootstrap.applicationRoot());
        assertEquals(Path.of("/repo"), bootstrap.projectRoot());
        assertEquals(Path.of("/repo/target"), bootstrap.targetDirectory());
        assertEquals(QuarkusBootstrapPreparerTestDoubles.FakeBootstrap.Mode.PROD, bootstrap.mode());
        assertSame(model, bootstrap.existingModel());
        assertEquals(
                List.of(new QuarkusBootstrapPreparerTestDoubles.FakeArtifactKey("com.example", "demo", "", "jar")),
                bootstrap.localArtifacts());
    }

    @Test
    void rejectsMissingBootstrapClasses() {
        QuarkusBootstrapApi api = new QuarkusBootstrapApi(
                "missing.QuarkusBootstrap",
                "missing.AugmentAction",
                QuarkusBootstrapPreparerTestDoubles.FakeBootstrap.Builder.class.getName(),
                QuarkusBootstrapPreparerTestDoubles.FakeBootstrap.Mode.class.getName());

        QuarkusAugmentationException exception = assertThrows(
                QuarkusAugmentationException.class,
                () -> preparer.prepare(descriptor(), api, applicationModel()));

        assertTrue(exception.getMessage().contains("bootstrap classes are missing"));
    }

    @Test
    void rejectsIncompatibleBuilderApi() {
        QuarkusBootstrapApi api = new QuarkusBootstrapApi(
                QuarkusBootstrapPreparerTestDoubles.IncompatibleBootstrap.class.getName(),
                QuarkusBootstrapPreparerTestDoubles.FakeAugmentAction.class.getName(),
                QuarkusBootstrapPreparerTestDoubles.IncompatibleBootstrap.Builder.class.getName(),
                QuarkusBootstrapPreparerTestDoubles.IncompatibleBootstrap.Mode.class.getName());

        QuarkusAugmentationException exception = assertThrows(
                QuarkusAugmentationException.class,
                () -> preparer.prepare(descriptor(), api, applicationModel()));

        assertTrue(exception.getMessage().contains("bootstrap builder API is incompatible"));
        assertTrue(exception.getMessage().contains("setExistingModel"));
    }

    private static QuarkusApplicationModelHandle applicationModel() {
        QuarkusBootstrapPreparerTestDoubles.FakeApplicationModel model =
                new QuarkusBootstrapPreparerTestDoubles.FakeApplicationModel();
        return new QuarkusApplicationModelHandle(model, model.getClass().getName(), 0, 0, 0);
    }

    private static QuarkusBootstrapApi api() {
        return new QuarkusBootstrapApi(
                QuarkusBootstrapPreparerTestDoubles.FakeBootstrap.class.getName(),
                QuarkusBootstrapPreparerTestDoubles.FakeAugmentAction.class.getName(),
                QuarkusBootstrapPreparerTestDoubles.FakeBootstrap.Builder.class.getName(),
                QuarkusBootstrapPreparerTestDoubles.FakeBootstrap.Mode.class.getName());
    }

    private static QuarkusBootstrapDescriptor descriptor() {
        return new QuarkusBootstrapDescriptor(
                Path.of("/repo/target/quarkus/zolt-bootstrap.properties"),
                Path.of("/repo/target/quarkus/runtime-classpath.txt"),
                Path.of("/repo/target/quarkus/deployment-classpath.txt"),
                Path.of("/repo/target/quarkus/platform-properties.txt"),
                Path.of("/repo/target/quarkus/application-model.properties"),
                QuarkusBootstrapPreparerTestDoubles.FakeBootstrap.class.getName(),
                QuarkusBootstrapPreparerTestDoubles.FakeAugmentAction.class.getName(),
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
                List.of(),
                List.of());
    }

}
