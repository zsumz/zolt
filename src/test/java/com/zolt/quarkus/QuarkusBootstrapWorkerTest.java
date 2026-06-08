package com.zolt.quarkus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class QuarkusBootstrapWorkerTest {
    @TempDir
    private Path projectDir;

    @Test
    void requiresDescriptorArgument() {
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        QuarkusBootstrapWorker worker = new QuarkusBootstrapWorker(
                new QuarkusBootstrapDescriptorReader(),
                new QuarkusBootstrapApiProbe(),
                modelFactory(),
                new QuarkusBootstrapPreparer(),
                new QuarkusCuratedApplicationInvoker(),
                new QuarkusProductionApplicationCreator(),
                new QuarkusProductionApplicationSummarizer(),
                new QuarkusProductionOutputValidator(),
                new QuarkusProductionOutputVerifier(),
                new QuarkusBootstrapWorkerResultCodec(),
                new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8));

        int exitCode = worker.run(new String[0]);

        assertEquals(2, exitCode);
        assertTrue(err.toString(StandardCharsets.UTF_8).contains("requires exactly one descriptor path"));
    }

    @Test
    void createsProductionApplicationAndEmitsVerifiedOutputResult() throws IOException {
        Path descriptorFile = writeDescriptor();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        QuarkusBootstrapWorker worker = new QuarkusBootstrapWorker(
                new QuarkusBootstrapDescriptorReader(),
                new QuarkusBootstrapApiProbe(),
                modelFactory(),
                new QuarkusBootstrapPreparer(),
                new QuarkusCuratedApplicationInvoker(),
                new QuarkusProductionApplicationCreator(),
                new QuarkusProductionApplicationSummarizer(),
                new QuarkusProductionOutputValidator(),
                new QuarkusProductionOutputVerifier(),
                new QuarkusBootstrapWorkerResultCodec(),
                new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8));

        int exitCode = worker.run(new String[] {descriptorFile.toString()});

        assertEquals(0, exitCode);
        QuarkusBootstrapWorkerResult result = new QuarkusBootstrapWorkerResultCodec()
                .parse(out.toString(StandardCharsets.UTF_8))
                .orElseThrow();
        assertEquals("sha256:" + "1".repeat(64), result.inputFingerprint());
        assertEquals(projectDir.resolve("target/quarkus-app"), result.packageDirectory());
        assertEquals(projectDir.resolve("target/quarkus-app/quarkus-run.jar"), result.runnerJar());
        assertEquals(projectDir.resolve("target/quarkus-app/lib"), result.libraryDirectory());
        assertEquals(1, result.artifactResultCount());
    }

    @Test
    void reportsDescriptorReadErrors() {
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        QuarkusBootstrapWorker worker = new QuarkusBootstrapWorker(
                new QuarkusBootstrapDescriptorReader(),
                new QuarkusBootstrapApiProbe(),
                modelFactory(),
                new QuarkusBootstrapPreparer(),
                new QuarkusCuratedApplicationInvoker(),
                new QuarkusProductionApplicationCreator(),
                new QuarkusProductionApplicationSummarizer(),
                new QuarkusProductionOutputValidator(),
                new QuarkusProductionOutputVerifier(),
                new QuarkusBootstrapWorkerResultCodec(),
                new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8));

        int exitCode = worker.run(new String[] {projectDir.resolve("missing.properties").toString()});

        assertEquals(1, exitCode);
        assertTrue(err.toString(StandardCharsets.UTF_8).contains("Could not read Quarkus bootstrap descriptor"));
    }

    private Path writeDescriptor() throws IOException {
        Path augmentationDirectory = projectDir.resolve("target/quarkus");
        Files.createDirectories(augmentationDirectory);
        Path runtimeClasspathFile = augmentationDirectory.resolve("runtime-classpath.txt");
        Path deploymentClasspathFile = augmentationDirectory.resolve("deployment-classpath.txt");
        Path applicationModelFile = augmentationDirectory.resolve("application-model.properties");
        Files.writeString(runtimeClasspathFile, "", StandardCharsets.UTF_8);
        Files.writeString(deploymentClasspathFile, "", StandardCharsets.UTF_8);
        Files.writeString(
                applicationModelFile,
                """
                version=1
                application.groupId=com.example
                application.artifactId=demo
                application.version=1.0.0
                application.classifier=
                application.type=jar
                application.path=%s
                dependencyCount=1
                dependency.0.groupId=io.quarkus
                dependency.0.artifactId=quarkus-rest
                dependency.0.version=3.33.0
                dependency.0.classifier=
                dependency.0.type=jar
                dependency.0.scope=compile
                dependency.0.path=%s
                dependency.0.direct=true
                """.formatted(
                        projectDir.resolve("target/classes"),
                        projectDir.resolve(".zolt/cache/io/quarkus/quarkus-rest.jar")),
                StandardCharsets.UTF_8);
        Path descriptorFile = augmentationDirectory.resolve("zolt-bootstrap.properties");
        Files.writeString(
                descriptorFile,
                """
                version=1
                bootstrapClass=%s
                augmentActionClass=%s
                mode=prod
                package=fast-jar
                projectDirectory=%s
                applicationClasses=%s
                augmentationDirectory=%s
                packageDirectory=%s
                runtimeClasspathFile=%s
                deploymentClasspathFile=%s
                applicationModelFile=%s
                inputFingerprint=%s
                """.formatted(
                        WorkerBootstrap.class.getName(),
                        WorkerAugmentAction.class.getName(),
                        projectDir,
                        projectDir.resolve("target/classes"),
                        augmentationDirectory,
                        projectDir.resolve("target/quarkus-app"),
                        runtimeClasspathFile,
                        deploymentClasspathFile,
                        applicationModelFile,
                        "sha256:" + "1".repeat(64)),
                StandardCharsets.UTF_8);
        return descriptorFile;
    }

    public static final class WorkerBootstrap {
        public enum Mode {
            PROD
        }

        public static Builder builder() {
            return new Builder();
        }

        private final Path augmentationDirectory;

        WorkerBootstrap(Path augmentationDirectory) {
            this.augmentationDirectory = augmentationDirectory;
        }

        public WorkerCuratedApplication bootstrap() {
            return new WorkerCuratedApplication(augmentationDirectory.resolve("quarkus-app"));
        }

        public static final class Builder {
            private Path targetDirectory;

            public Builder setApplicationRoot(Path ignored) {
                return this;
            }

            public Builder setProjectRoot(Path ignored) {
                return this;
            }

            public Builder setTargetDirectory(Path targetDirectory) {
                this.targetDirectory = targetDirectory;
                return this;
            }

            public Builder setMode(Mode ignored) {
                return this;
            }

            public Builder setExistingModel(FakeApplicationModel ignored) {
                return this;
            }

            public WorkerBootstrap build() {
                return new WorkerBootstrap(targetDirectory);
            }
        }
    }

    public interface WorkerAugmentAction {
        Object createProductionApplication();
    }

    public static final class WorkerCuratedApplication {
        private final Path packageDirectory;

        WorkerCuratedApplication(Path packageDirectory) {
            this.packageDirectory = packageDirectory;
        }

        public WorkerAugmentAction createAugmentor() {
            return new WorkerAugmentActionImpl(packageDirectory);
        }
    }

    public static final class WorkerAugmentActionImpl implements WorkerAugmentAction {
        private final Path packageDirectory;

        WorkerAugmentActionImpl(Path packageDirectory) {
            this.packageDirectory = packageDirectory;
        }

        @Override
        public Object createProductionApplication() {
            try {
                Files.createDirectories(packageDirectory.resolve("lib"));
                Files.writeString(packageDirectory.resolve("quarkus-run.jar"), "runner", StandardCharsets.UTF_8);
            } catch (IOException exception) {
                throw new IllegalStateException("could not write fake Quarkus output", exception);
            }
            return new WorkerAugmentResult(packageDirectory);
        }
    }

    public static final class WorkerAugmentResult {
        private final Path packageDirectory;

        WorkerAugmentResult(Path packageDirectory) {
            this.packageDirectory = packageDirectory;
        }

        public java.util.List<Object> getResults() {
            return java.util.List.of(new Object());
        }

        public WorkerJarResult getJar() {
            return new WorkerJarResult(packageDirectory);
        }

        public Path getNativeResult() {
            return null;
        }
    }

    public static final class WorkerJarResult {
        private final Path packageDirectory;

        WorkerJarResult(Path packageDirectory) {
            this.packageDirectory = packageDirectory;
        }

        public Path getPath() {
            return packageDirectory.resolve("quarkus-run.jar");
        }

        public Path getLibraryDir() {
            return packageDirectory.resolve("lib");
        }

        public boolean isUberJar() {
            return false;
        }
    }

    private static QuarkusApplicationModelFactory modelFactory() {
        return new QuarkusApplicationModelFactory(new QuarkusApplicationModelApi(
                FakeApplicationModelBuilder.class.getName(),
                FakeResolvedDependencyBuilder.class.getName(),
                FakePlatformImports.class.getName(),
                FakePlatformImportsImpl.class.getName()));
    }

    public static final class FakeApplicationModelBuilder {
        private FakeResolvedDependencyBuilder appArtifact;
        private final java.util.List<FakeResolvedDependencyBuilder> dependencies = new java.util.ArrayList<>();

        public FakeApplicationModelBuilder setAppArtifact(FakeResolvedDependencyBuilder appArtifact) {
            this.appArtifact = appArtifact;
            return this;
        }

        public FakeApplicationModelBuilder addDependency(FakeResolvedDependencyBuilder dependency) {
            dependencies.add(dependency);
            return this;
        }

        public FakeApplicationModel build() {
            return new FakeApplicationModel(appArtifact, java.util.List.copyOf(dependencies));
        }
    }

    public static final class FakeResolvedDependencyBuilder {
        public static FakeResolvedDependencyBuilder newInstance() {
            return new FakeResolvedDependencyBuilder();
        }

        public FakeResolvedDependencyBuilder setGroupId(String ignored) {
            return this;
        }

        public FakeResolvedDependencyBuilder setArtifactId(String ignored) {
            return this;
        }

        public FakeResolvedDependencyBuilder setVersion(String ignored) {
            return this;
        }

        public FakeResolvedDependencyBuilder setClassifier(String ignored) {
            return this;
        }

        public FakeResolvedDependencyBuilder setType(String ignored) {
            return this;
        }

        public FakeResolvedDependencyBuilder setScope(String ignored) {
            return this;
        }

        public FakeResolvedDependencyBuilder setResolvedPath(Path ignored) {
            return this;
        }

        public FakeResolvedDependencyBuilder setDirect(boolean ignored) {
            return this;
        }

        public FakeResolvedDependencyBuilder setRuntimeCp() {
            return this;
        }

        public FakeResolvedDependencyBuilder setDeploymentCp() {
            return this;
        }
    }

    public interface FakePlatformImports {
    }

    public static final class FakePlatformImportsImpl implements FakePlatformImports {
        public void setPlatformProperties(java.util.Map<String, String> ignored) {
        }
    }

    public record FakeApplicationModel(
            FakeResolvedDependencyBuilder appArtifact,
            java.util.List<FakeResolvedDependencyBuilder> dependencies) {
    }
}
