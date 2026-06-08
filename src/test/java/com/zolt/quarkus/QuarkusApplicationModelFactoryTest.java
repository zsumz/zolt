package com.zolt.quarkus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.resolve.DependencyScope;
import com.zolt.resolve.PackageId;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class QuarkusApplicationModelFactoryTest {
    @Test
    void buildsApplicationModelFromDescriptorDependencies() {
        QuarkusApplicationModelFactory factory = new QuarkusApplicationModelFactory(new QuarkusApplicationModelApi(
                FakeApplicationModelBuilder.class.getName(),
                FakeResolvedDependencyBuilder.class.getName()));

        QuarkusApplicationModelHandle handle = factory.create(descriptor());

        assertEquals(FakeApplicationModel.class.getName(), handle.applicationModelClass());
        assertEquals(2, handle.dependencyCount());
        assertEquals(1, handle.runtimeDependencyCount());
        assertEquals(1, handle.deploymentDependencyCount());
        FakeApplicationModel model = assertInstanceOf(FakeApplicationModel.class, handle.applicationModel());
        assertEquals("com.example", model.appArtifact().groupId());
        assertEquals("demo", model.appArtifact().artifactId());
        assertEquals("1.0.0", model.appArtifact().version());
        assertEquals("compile", model.appArtifact().scope());
        assertEquals(Path.of("/repo/target/classes"), model.appArtifact().path());
        assertTrue(model.appArtifact().runtimeClasspath());
        assertFalse(model.appArtifact().deploymentClasspath());

        FakeResolvedDependencyBuilder runtime = model.dependencies().get(0);
        assertEquals("io.quarkus", runtime.groupId());
        assertEquals("quarkus-rest", runtime.artifactId());
        assertEquals("compile", runtime.scope());
        assertTrue(runtime.runtimeClasspath());
        assertFalse(runtime.deploymentClasspath());
        assertTrue(runtime.direct());

        FakeResolvedDependencyBuilder deployment = model.dependencies().get(1);
        assertEquals("io.quarkus", deployment.groupId());
        assertEquals("quarkus-rest-deployment", deployment.artifactId());
        assertEquals("compile", deployment.scope());
        assertFalse(deployment.runtimeClasspath());
        assertTrue(deployment.deploymentClasspath());
        assertFalse(deployment.direct());
    }

    @Test
    void rejectsMissingApplicationModelClasses() {
        QuarkusApplicationModelFactory factory = new QuarkusApplicationModelFactory(new QuarkusApplicationModelApi(
                "missing.ApplicationModelBuilder",
                FakeResolvedDependencyBuilder.class.getName()));

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
                FakeResolvedDependencyBuilder.class.getName()));

        QuarkusAugmentationException exception = assertThrows(
                QuarkusAugmentationException.class,
                () -> factory.create(descriptor()));

        assertTrue(exception.getMessage().contains("application model API is incompatible"));
        assertTrue(exception.getMessage().contains("setAppArtifact"));
    }

    private static QuarkusBootstrapDescriptor descriptor() {
        return new QuarkusBootstrapDescriptor(
                Path.of("/repo/target/quarkus/zolt-bootstrap.properties"),
                Path.of("/repo/target/quarkus/runtime-classpath.txt"),
                Path.of("/repo/target/quarkus/deployment-classpath.txt"),
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
                List.of(Path.of("/cache/quarkus-rest.jar")),
                List.of(Path.of("/cache/quarkus-rest-deployment.jar")),
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

    public static final class FakeApplicationModelBuilder {
        private FakeResolvedDependencyBuilder appArtifact;
        private final List<FakeResolvedDependencyBuilder> dependencies = new ArrayList<>();

        public FakeApplicationModelBuilder setAppArtifact(FakeResolvedDependencyBuilder appArtifact) {
            this.appArtifact = appArtifact;
            return this;
        }

        public FakeApplicationModelBuilder addDependency(FakeResolvedDependencyBuilder dependency) {
            dependencies.add(dependency);
            return this;
        }

        public FakeApplicationModel build() {
            return new FakeApplicationModel(appArtifact, List.copyOf(dependencies));
        }
    }

    public static final class IncompatibleApplicationModelBuilder {
        public IncompatibleApplicationModelBuilder addDependency(FakeResolvedDependencyBuilder dependency) {
            return this;
        }

        public FakeApplicationModel build() {
            return new FakeApplicationModel(null, List.of());
        }
    }

    public static final class FakeResolvedDependencyBuilder {
        private String groupId;
        private String artifactId;
        private String version;
        private String classifier;
        private String type;
        private String scope;
        private Path path;
        private boolean direct;
        private boolean runtimeClasspath;
        private boolean deploymentClasspath;

        public static FakeResolvedDependencyBuilder newInstance() {
            return new FakeResolvedDependencyBuilder();
        }

        public FakeResolvedDependencyBuilder setGroupId(String groupId) {
            this.groupId = groupId;
            return this;
        }

        public FakeResolvedDependencyBuilder setArtifactId(String artifactId) {
            this.artifactId = artifactId;
            return this;
        }

        public FakeResolvedDependencyBuilder setVersion(String version) {
            this.version = version;
            return this;
        }

        public FakeResolvedDependencyBuilder setClassifier(String classifier) {
            this.classifier = classifier;
            return this;
        }

        public FakeResolvedDependencyBuilder setType(String type) {
            this.type = type;
            return this;
        }

        public FakeResolvedDependencyBuilder setScope(String scope) {
            this.scope = scope;
            return this;
        }

        public FakeResolvedDependencyBuilder setResolvedPath(Path path) {
            this.path = path;
            return this;
        }

        public FakeResolvedDependencyBuilder setDirect(boolean direct) {
            this.direct = direct;
            return this;
        }

        public FakeResolvedDependencyBuilder setRuntimeCp() {
            this.runtimeClasspath = true;
            return this;
        }

        public FakeResolvedDependencyBuilder setDeploymentCp() {
            this.deploymentClasspath = true;
            return this;
        }

        String groupId() {
            return groupId;
        }

        String artifactId() {
            return artifactId;
        }

        String version() {
            return version;
        }

        String classifier() {
            return classifier;
        }

        String type() {
            return type;
        }

        String scope() {
            return scope;
        }

        Path path() {
            return path;
        }

        boolean direct() {
            return direct;
        }

        boolean runtimeClasspath() {
            return runtimeClasspath;
        }

        boolean deploymentClasspath() {
            return deploymentClasspath;
        }
    }

    public record FakeApplicationModel(
            FakeResolvedDependencyBuilder appArtifact,
            List<FakeResolvedDependencyBuilder> dependencies) {
    }
}
