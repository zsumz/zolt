package com.zolt.quarkus.bootstrap;

import com.zolt.quarkus.bootstrap.descriptor.QuarkusBootstrapDescriptorReader;
import com.zolt.quarkus.production.QuarkusProductionApplicationCreator;
import com.zolt.quarkus.production.QuarkusProductionApplicationSummarizer;
import com.zolt.quarkus.production.QuarkusProductionOutputValidator;
import com.zolt.quarkus.production.QuarkusProductionOutputVerifier;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class QuarkusBootstrapWorkerFixture {
    private QuarkusBootstrapWorkerFixture() {
    }

    static QuarkusBootstrapWorker worker(PrintStream out, PrintStream err) {
        return new QuarkusBootstrapWorker(
                new QuarkusBootstrapWorkerDependencies(
                        new QuarkusBootstrapDescriptorReader(),
                        new QuarkusBootstrapApiProbe(),
                        modelFactory(),
                        new QuarkusBootstrapPreparer(),
                        new QuarkusCuratedApplicationInvoker(),
                        new QuarkusProductionApplicationCreator(),
                        new QuarkusProductionApplicationSummarizer(),
                        new QuarkusProductionOutputValidator(),
                        new QuarkusProductionOutputVerifier(),
                        new QuarkusBootstrapWorkerResultCodec()),
                out,
                err);
    }

    static Path writeDescriptor(Path projectDir) throws IOException {
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

    private static QuarkusApplicationModelFactory modelFactory() {
        return new QuarkusApplicationModelFactory(new QuarkusApplicationModelApi(
                QuarkusBootstrapWorkerTestDoubles.FakeApplicationModelBuilder.class.getName(),
                QuarkusBootstrapWorkerTestDoubles.FakeResolvedDependencyBuilder.class.getName(),
                QuarkusBootstrapWorkerTestDoubles.FakePlatformImports.class.getName(),
                QuarkusBootstrapWorkerTestDoubles.FakePlatformImportsImpl.class.getName()));
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

            public Builder setExistingModel(QuarkusBootstrapWorkerTestDoubles.FakeApplicationModel ignored) {
                return this;
            }

            public Builder addLocalArtifact(QuarkusBootstrapWorkerTestDoubles.FakeArtifactKey ignored) {
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

}
