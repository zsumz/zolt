package sh.zolt.quarkus.bootstrap;

import java.nio.file.Path;
import java.util.List;

final class QuarkusBootstrapPreparerTestDoubles {
    private QuarkusBootstrapPreparerTestDoubles() {
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
        private final List<FakeArtifactKey> localArtifacts;

        private FakeBootstrap(Builder builder) {
            this.applicationRoot = builder.applicationRoot;
            this.projectRoot = builder.projectRoot;
            this.targetDirectory = builder.targetDirectory;
            this.mode = builder.mode;
            this.existingModel = builder.existingModel;
            this.localArtifacts = List.copyOf(builder.localArtifacts);
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

        List<FakeArtifactKey> localArtifacts() {
            return localArtifacts;
        }

        public static final class Builder {
            private Path applicationRoot;
            private Path projectRoot;
            private Path targetDirectory;
            private Mode mode;
            private FakeApplicationModel existingModel;
            private final List<FakeArtifactKey> localArtifacts = new java.util.ArrayList<>();

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

            public Builder addLocalArtifact(FakeArtifactKey artifactKey) {
                localArtifacts.add(artifactKey);
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

    public record FakeArtifactKey(String groupId, String artifactId, String classifier, String type) {
        public static FakeArtifactKey of(String groupId, String artifactId, String classifier, String type) {
            return new FakeArtifactKey(groupId, artifactId, classifier, type);
        }
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
