package sh.zolt.quarkus.bootstrap;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import sh.zolt.dependency.DependencyScope;
import sh.zolt.dependency.PackageId;
import sh.zolt.quarkus.QuarkusAugmentationException;
import sh.zolt.quarkus.bootstrap.QuarkusApplicationModelFactoryArtifactDoubles.FakePlatformImports;
import sh.zolt.quarkus.bootstrap.QuarkusApplicationModelFactoryArtifactDoubles.FakePlatformImportsImpl;
import sh.zolt.quarkus.bootstrap.QuarkusApplicationModelFactoryModelDoubles.FakeResolvedDependencyBuilder;
import sh.zolt.quarkus.bootstrap.QuarkusApplicationModelFactoryModelDoubles.IncompatibleApplicationModelBuilder;
import sh.zolt.quarkus.bootstrap.descriptor.QuarkusApplicationArtifact;
import sh.zolt.quarkus.bootstrap.descriptor.QuarkusBootstrapDependency;
import sh.zolt.quarkus.bootstrap.descriptor.QuarkusBootstrapDescriptor;
import sh.zolt.quarkus.bootstrap.descriptor.QuarkusBootstrapDescriptorWriter;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

final class QuarkusApplicationModelFactoryFailureTest {
    @Test
    void rejectsMissingApplicationModelClasses() {
        QuarkusApplicationModelFactory factory = new QuarkusApplicationModelFactory(new QuarkusApplicationModelApi(
                "missing.ApplicationModelBuilder",
                FakeResolvedDependencyBuilder.class.getName(),
                FakePlatformImports.class.getName(),
                FakePlatformImportsImpl.class.getName()));

        QuarkusAugmentationException exception = assertThrows(
                QuarkusAugmentationException.class,
                () -> factory.create(descriptor()));

        assertTrue(exception.getMessage().contains("application model classes are missing"));
        assertTrue(exception.getMessage().contains("quarkus-bootstrap-app-model"));
    }

    @Test
    void rejectsIncompatibleApplicationModelApi() {
        QuarkusApplicationModelFactory factory = new QuarkusApplicationModelFactory(new QuarkusApplicationModelApi(
                IncompatibleApplicationModelBuilder.class.getName(),
                FakeResolvedDependencyBuilder.class.getName(),
                FakePlatformImports.class.getName(),
                FakePlatformImportsImpl.class.getName()));

        QuarkusAugmentationException exception = assertThrows(
                QuarkusAugmentationException.class,
                () -> factory.create(descriptor()));

        assertTrue(exception.getMessage().contains("application model API is incompatible"));
        assertTrue(exception.getMessage().contains("setAppArtifact"));
    }

    private static QuarkusBootstrapDescriptor descriptor() {
        return descriptor(List.of());
    }

    private static QuarkusBootstrapDescriptor descriptor(List<Path> platformPropertiesFiles) {
        return descriptor(
                platformPropertiesFiles,
                List.of(
                        new QuarkusBootstrapDependency(
                                new PackageId("io.quarkus", "quarkus-rest"),
                                "3.33.0",
                                DependencyScope.COMPILE,
                                Path.of("/cache/quarkus-rest.jar"),
                                true),
                        new QuarkusBootstrapDependency(
                                new PackageId("io.quarkus", "quarkus-rest-deployment"),
                                "3.33.0",
                                DependencyScope.QUARKUS_DEPLOYMENT,
                                Path.of("/cache/quarkus-rest-deployment.jar"),
                                false)));
    }

    private static QuarkusBootstrapDescriptor descriptor(
            List<Path> platformPropertiesFiles,
            List<QuarkusBootstrapDependency> bootstrapDependencies) {
        return descriptor(
                platformPropertiesFiles,
                bootstrapDependencies,
                List.of(Path.of("/cache/quarkus-rest.jar")),
                List.of(Path.of("/cache/quarkus-rest-deployment.jar")));
    }

    private static QuarkusBootstrapDescriptor descriptor(
            List<Path> platformPropertiesFiles,
            List<QuarkusBootstrapDependency> bootstrapDependencies,
            List<Path> runtimeClasspath,
            List<Path> deploymentClasspath) {
        return new QuarkusBootstrapDescriptor(
                Path.of("/repo/target/quarkus/zolt-bootstrap.properties"),
                Path.of("/repo/target/quarkus/runtime-classpath.txt"),
                Path.of("/repo/target/quarkus/deployment-classpath.txt"),
                Path.of("/repo/target/quarkus/platform-properties.txt"),
                Path.of("/repo/target/quarkus/application-model.properties"),
                QuarkusBootstrapDescriptorWriter.BOOTSTRAP_CLASS,
                QuarkusBootstrapDescriptorWriter.AUGMENT_ACTION_CLASS,
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
                runtimeClasspath,
                deploymentClasspath,
                platformPropertiesFiles,
                bootstrapDependencies);
    }
}
