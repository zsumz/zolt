package com.zolt.quarkus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.resolve.DependencyScope;
import com.zolt.resolve.PackageId;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class QuarkusApplicationModelFactoryTest {
    @TempDir
    private Path tempDir;

    @Test
    void buildsApplicationModelFromDescriptorDependencies() {
        QuarkusApplicationModelFactory factory = new QuarkusApplicationModelFactory(fakeApi());

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
    void addsPlatformPropertiesToApplicationModel() throws IOException {
        Path platformProperties = tempDir.resolve("quarkus-platform.properties");
        Files.writeString(platformProperties, """
                platform.quarkus.native.builder-image=quay.io/quarkus/ubi-quarkus-mandrel-builder-image:jdk-21
                zolt.example=true
                """);
        QuarkusApplicationModelFactory factory = new QuarkusApplicationModelFactory(fakeApi());

        QuarkusApplicationModelHandle handle = factory.create(descriptor(List.of(platformProperties)));

        FakeApplicationModel model = assertInstanceOf(FakeApplicationModel.class, handle.applicationModel());
        FakePlatformImportsImpl platformImports =
                assertInstanceOf(FakePlatformImportsImpl.class, model.platformImports());
        assertEquals(
                "quay.io/quarkus/ubi-quarkus-mandrel-builder-image:jdk-21",
                platformImports.properties().get("platform.quarkus.native.builder-image"));
        assertEquals("true", platformImports.properties().get("zolt.example"));
    }

    @Test
    void mergesRuntimeAndDeploymentEntriesForSameArtifactKey() {
        QuarkusApplicationModelFactory factory = new QuarkusApplicationModelFactory(fakeApi());

        QuarkusApplicationModelHandle handle = factory.create(descriptor(
                List.of(),
                List.of(
                        new QuarkusBootstrapDependency(
                                new PackageId("io.smallrye.common", "smallrye-common-os"),
                                "2.13.4",
                                DependencyScope.COMPILE,
                                Path.of("/cache/smallrye-common-os.jar"),
                                false),
                        new QuarkusBootstrapDependency(
                                new PackageId("io.smallrye.common", "smallrye-common-os"),
                                "2.13.4",
                                DependencyScope.QUARKUS_DEPLOYMENT,
                                Path.of("/cache/smallrye-common-os.jar"),
                                false))));

        assertEquals(1, handle.dependencyCount());
        assertEquals(1, handle.runtimeDependencyCount());
        assertEquals(1, handle.deploymentDependencyCount());
        FakeApplicationModel model = assertInstanceOf(FakeApplicationModel.class, handle.applicationModel());
        FakeResolvedDependencyBuilder dependency = model.dependencies().get(0);
        assertEquals("io.smallrye.common", dependency.groupId());
        assertEquals("smallrye-common-os", dependency.artifactId());
        assertTrue(dependency.runtimeClasspath());
        assertTrue(dependency.deploymentClasspath());
    }

    @Test
    void addsClassLoadingArtifactsFromRuntimeExtensionMetadata() throws IOException {
        Path quarkusCore = tempDir.resolve("quarkus-core.jar");
        writeJar(
                quarkusCore,
                QuarkusExtensionMetadataReader.METADATA_PATH,
                """
                deployment-artifact=io.quarkus:quarkus-core-deployment:3.33.2
                parent-first-artifacts=io.quarkus:quarkus-bootstrap-runner::jar
                runner-parent-first-artifacts=io.quarkus:quarkus-bootstrap-runner::jar
                """);
        QuarkusApplicationModelFactory factory = new QuarkusApplicationModelFactory(fakeApiWithArtifactKey());

        QuarkusApplicationModelHandle handle = factory.create(descriptor(
                List.of(),
                List.of(new QuarkusBootstrapDependency(
                        new PackageId("io.quarkus", "quarkus-bootstrap-runner"),
                        "3.33.2",
                        DependencyScope.COMPILE,
                        Path.of("/cache/quarkus-bootstrap-runner.jar"),
                        false)),
                List.of(quarkusCore),
                List.of()));

        FakeApplicationModel model = assertInstanceOf(FakeApplicationModel.class, handle.applicationModel());
        assertEquals(
                List.of(new FakeArtifactKey("io.quarkus", "quarkus-bootstrap-runner", "", "jar")),
                model.parentFirstArtifacts());
        assertEquals(
                List.of(new FakeArtifactKey("io.quarkus", "quarkus-bootstrap-runner", "", "jar")),
                model.runnerParentFirstArtifacts());
    }

    @Test
    void addsClassLoadingArtifactsFromOptions() {
        QuarkusApplicationModelFactory factory = new QuarkusApplicationModelFactory(fakeApiWithArtifactKey());

        QuarkusApplicationModelHandle handle = factory.create(
                descriptor(),
                java.util.Optional.empty(),
                new QuarkusApplicationModelOptions(
                        List.of(new QuarkusArtifactKey(
                                "io.quarkus",
                                "quarkus-builder",
                                java.util.Optional.empty(),
                                java.util.Optional.empty())),
                        List.of(),
                        Map.of()));

        FakeApplicationModel model = assertInstanceOf(FakeApplicationModel.class, handle.applicationModel());
        assertEquals(
                List.of(new FakeArtifactKey("io.quarkus", "quarkus-builder", "", "jar")),
                model.parentFirstArtifacts());
        assertEquals(List.of(), model.runnerParentFirstArtifacts());
    }

    @Test
    void deduplicatesClassLoadingArtifactsFromOptionsAndRuntimeExtensionMetadata() throws IOException {
        Path quarkusCore = tempDir.resolve("quarkus-core.jar");
        writeJar(
                quarkusCore,
                QuarkusExtensionMetadataReader.METADATA_PATH,
                """
                deployment-artifact=io.quarkus:quarkus-core-deployment:3.33.2
                parent-first-artifacts=io.quarkus:quarkus-builder::jar
                """);
        QuarkusApplicationModelFactory factory = new QuarkusApplicationModelFactory(fakeApiWithArtifactKey());

        QuarkusApplicationModelHandle handle = factory.create(
                descriptor(
                        List.of(),
                        List.of(new QuarkusBootstrapDependency(
                                new PackageId("io.quarkus", "quarkus-builder"),
                                "3.33.2",
                                DependencyScope.COMPILE,
                                Path.of("/cache/quarkus-builder.jar"),
                                false)),
                        List.of(quarkusCore),
                        List.of()),
                java.util.Optional.empty(),
                QuarkusApplicationModelOptions.TEST_BOOTSTRAP);

        FakeApplicationModel model = assertInstanceOf(FakeApplicationModel.class, handle.applicationModel());
        assertEquals(
                List.of(
                        new FakeArtifactKey("io.quarkus", "quarkus-builder", "", "jar"),
                        new FakeArtifactKey("org.eclipse.microprofile.config", "microprofile-config-api", "", "jar"),
                        new FakeArtifactKey("io.smallrye.config", "smallrye-config", "", "jar"),
                        new FakeArtifactKey("io.smallrye.config", "smallrye-config-common", "", "jar"),
                        new FakeArtifactKey("io.smallrye.config", "smallrye-config-core", "", "jar")),
                model.parentFirstArtifacts());
    }

    @Test
    void addsRemovedResourcesFromRuntimeExtensionMetadata() throws IOException {
        Path quarkusRest = tempDir.resolve("quarkus-rest.jar");
        writeJar(
                quarkusRest,
                QuarkusExtensionMetadataReader.METADATA_PATH,
                """
                deployment-artifact=io.quarkus:quarkus-rest-deployment:3.33.2
                removed-resources.io.quarkus\\:quarkus-rest=\
                META-INF/services/io.quarkus.runtime.test.TestHttpEndpointProvider,\
                META-INF/duplicate.index
                """);
        QuarkusApplicationModelFactory factory = new QuarkusApplicationModelFactory(fakeApiWithArtifactKey());

        QuarkusApplicationModelHandle handle = factory.create(descriptor(
                List.of(),
                List.of(new QuarkusBootstrapDependency(
                        new PackageId("io.quarkus", "quarkus-rest"),
                        "3.33.2",
                        DependencyScope.COMPILE,
                        quarkusRest,
                        true)),
                List.of(quarkusRest),
                List.of()));

        FakeApplicationModel model = assertInstanceOf(FakeApplicationModel.class, handle.applicationModel());
        assertEquals(
                Map.of(
                        new FakeArtifactKey("io.quarkus", "quarkus-rest", "", "jar"),
                        List.of(
                                "META-INF/services/io.quarkus.runtime.test.TestHttpEndpointProvider",
                                "META-INF/duplicate.index")),
                model.removedResources());
    }

    @Test
    void addsTestBootstrapRemovedResourceOption() {
        QuarkusApplicationModelFactory factory = new QuarkusApplicationModelFactory(fakeApiWithArtifactKey());

        QuarkusApplicationModelHandle handle = factory.create(
                descriptor(),
                java.util.Optional.empty(),
                QuarkusApplicationModelOptions.TEST_BOOTSTRAP);

        FakeApplicationModel model = assertInstanceOf(FakeApplicationModel.class, handle.applicationModel());
        assertEquals(
                Map.of(
                        new FakeArtifactKey("io.quarkus", "quarkus-rest", "", "jar"),
                        List.of("META-INF/services/io.quarkus.runtime.test.TestHttpEndpointProvider")),
                model.removedResources());
    }

    @Test
    void addsWorkspaceModuleTestOutputAsAdditionalTestClasspathElement() {
        QuarkusApplicationModelFactory factory = new QuarkusApplicationModelFactory(fakeApi());

        QuarkusApplicationModelHandle handle = factory.create(
                descriptor(),
                java.util.Optional.of(new QuarkusWorkspaceModuleInputs(
                        Path.of("/repo"),
                        Path.of("/repo/target"),
                        Path.of("/repo/src/main/java"),
                        Path.of("/repo/src/main/resources"),
                        Path.of("/repo/target/classes"),
                        Path.of("/repo/src/test/java"),
                        Path.of("/repo/src/test/resources"),
                        Path.of("/repo/target/test-classes"))));

        FakeApplicationModel model = assertInstanceOf(FakeApplicationModel.class, handle.applicationModel());
        io.quarkus.bootstrap.workspace.FakeWorkspaceModule module =
                assertInstanceOf(io.quarkus.bootstrap.workspace.FakeWorkspaceModule.class,
                        model.appArtifact().workspaceModule());
        assertEquals(Path.of("/repo/zolt.toml"), module.buildFile());
        assertEquals(List.of("/repo/target/test-classes"), module.additionalTestClasspathElements());
        assertEquals(
                List.of("", "tests"),
                module.artifactSources().stream()
                        .map(io.quarkus.bootstrap.workspace.FakeArtifactSources::classifier)
                        .toList());
    }

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

    private static QuarkusApplicationModelApi fakeApi() {
        return new QuarkusApplicationModelApi(
                FakeApplicationModelBuilder.class.getName(),
                FakeResolvedDependencyBuilder.class.getName(),
                FakePlatformImports.class.getName(),
                FakePlatformImportsImpl.class.getName());
    }

    private static QuarkusApplicationModelApi fakeApiWithArtifactKey() {
        return new QuarkusApplicationModelApi(
                FakeApplicationModelBuilder.class.getName(),
                FakeResolvedDependencyBuilder.class.getName(),
                FakePlatformImports.class.getName(),
                FakePlatformImportsImpl.class.getName(),
                FakeArtifactKey.class.getName());
    }

    private static void writeJar(Path jarPath, String entryName, String content) throws IOException {
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jarPath))) {
            output.putNextEntry(new JarEntry(entryName));
            output.write(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            output.closeEntry();
        }
    }

    public static final class FakeApplicationModelBuilder {
        private FakeResolvedDependencyBuilder appArtifact;
        private FakePlatformImports platformImports;
        private final List<FakeResolvedDependencyBuilder> dependencies = new ArrayList<>();
        private final List<FakeArtifactKey> parentFirstArtifacts = new ArrayList<>();
        private final List<FakeArtifactKey> runnerParentFirstArtifacts = new ArrayList<>();
        private final Map<FakeArtifactKey, List<String>> removedResources = new java.util.LinkedHashMap<>();
        private io.quarkus.bootstrap.workspace.FakeWorkspaceModule workspaceModule;

        public FakeApplicationModelBuilder setAppArtifact(FakeResolvedDependencyBuilder appArtifact) {
            this.appArtifact = appArtifact;
            return this;
        }

        public FakeApplicationModelBuilder setPlatformImports(FakePlatformImports platformImports) {
            this.platformImports = platformImports;
            return this;
        }

        public FakeApplicationModelBuilder addDependency(FakeResolvedDependencyBuilder dependency) {
            dependencies.add(dependency);
            return this;
        }

        public FakeApplicationModelBuilder addParentFirstArtifact(FakeArtifactKey artifactKey) {
            parentFirstArtifacts.add(artifactKey);
            return this;
        }

        public FakeApplicationModelBuilder addRunnerParentFirstArtifact(FakeArtifactKey artifactKey) {
            runnerParentFirstArtifacts.add(artifactKey);
            return this;
        }

        public FakeApplicationModelBuilder addRemovedResources(
                FakeArtifactKey artifactKey,
                java.util.Collection<String> resources) {
            removedResources.put(artifactKey, List.copyOf(resources));
            return this;
        }

        public io.quarkus.bootstrap.workspace.WorkspaceModule.Mutable getOrCreateProjectModule(
                io.quarkus.bootstrap.workspace.WorkspaceModuleId moduleId,
                java.io.File moduleDirectory,
                java.io.File buildDirectory) {
            workspaceModule = new io.quarkus.bootstrap.workspace.FakeWorkspaceModule(
                    moduleId,
                    moduleDirectory.toPath(),
                    buildDirectory.toPath());
            return workspaceModule;
        }

        public FakeApplicationModel build() {
            return new FakeApplicationModel(
                    appArtifact,
                    platformImports,
                    List.copyOf(dependencies),
                    List.copyOf(parentFirstArtifacts),
                    List.copyOf(runnerParentFirstArtifacts),
                    Map.copyOf(removedResources));
        }
    }

    public static final class IncompatibleApplicationModelBuilder {
        public IncompatibleApplicationModelBuilder addDependency(FakeResolvedDependencyBuilder dependency) {
            return this;
        }

        public FakeApplicationModel build() {
            return new FakeApplicationModel(null, null, List.of(), List.of(), List.of(), Map.of());
        }
    }

    public record FakeArtifactKey(String groupId, String artifactId, String classifier, String type) {
        public static FakeArtifactKey of(String groupId, String artifactId, String classifier, String type) {
            return new FakeArtifactKey(groupId, artifactId, classifier, type);
        }
    }

    public interface FakePlatformImports {
    }

    public static final class FakePlatformImportsImpl implements FakePlatformImports {
        private Map<String, String> properties = Map.of();

        public void setPlatformProperties(Map<String, String> properties) {
            this.properties = Map.copyOf(properties);
        }

        Map<String, String> properties() {
            return properties;
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
        private io.quarkus.bootstrap.workspace.WorkspaceModule workspaceModule;

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

        public FakeResolvedDependencyBuilder setWorkspaceModule(
                io.quarkus.bootstrap.workspace.WorkspaceModule workspaceModule) {
            this.workspaceModule = workspaceModule;
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

        io.quarkus.bootstrap.workspace.WorkspaceModule workspaceModule() {
            return workspaceModule;
        }
    }

    public record FakeApplicationModel(
            FakeResolvedDependencyBuilder appArtifact,
            FakePlatformImports platformImports,
            List<FakeResolvedDependencyBuilder> dependencies,
            List<FakeArtifactKey> parentFirstArtifacts,
            List<FakeArtifactKey> runnerParentFirstArtifacts,
            Map<FakeArtifactKey, List<String>> removedResources) {
    }
}
