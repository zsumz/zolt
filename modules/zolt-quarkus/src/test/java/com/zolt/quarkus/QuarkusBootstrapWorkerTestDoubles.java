package com.zolt.quarkus;

import java.nio.file.Path;

final class QuarkusBootstrapWorkerTestDoubles {
    private QuarkusBootstrapWorkerTestDoubles() {
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

    public record FakeArtifactKey(String groupId, String artifactId, String classifier, String type) {
        public static FakeArtifactKey of(String groupId, String artifactId, String classifier, String type) {
            return new FakeArtifactKey(groupId, artifactId, classifier, type);
        }
    }

    public record FakeApplicationModel(
            FakeResolvedDependencyBuilder appArtifact,
            java.util.List<FakeResolvedDependencyBuilder> dependencies) {
    }
}
