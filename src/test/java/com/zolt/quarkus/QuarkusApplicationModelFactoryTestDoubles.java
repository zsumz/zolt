package com.zolt.quarkus;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class QuarkusApplicationModelFactoryTestDoubles {
    private QuarkusApplicationModelFactoryTestDoubles() {
    }

    static QuarkusApplicationModelApi fakeApi() {
        return new QuarkusApplicationModelApi(
                FakeApplicationModelBuilder.class.getName(),
                FakeResolvedDependencyBuilder.class.getName(),
                FakePlatformImports.class.getName(),
                FakePlatformImportsImpl.class.getName(),
                FakeArtifactKey.class.getName());
    }

    static QuarkusApplicationModelApi fakeApiWithArtifactKey() {
        return fakeApi();
    }

    public static final class FakeApplicationModelBuilder {
        private FakeResolvedDependencyBuilder appArtifact;
        private FakePlatformImports platformImports;
        private final List<FakeResolvedDependencyBuilder> dependencies = new ArrayList<>();
        private final List<FakeArtifactKey> parentFirstArtifacts = new ArrayList<>();
        private final List<FakeArtifactKey> runnerParentFirstArtifacts = new ArrayList<>();
        private final List<FakeArtifactKey> reloadableWorkspaceModules = new ArrayList<>();
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

        public FakeApplicationModelBuilder addReloadableWorkspaceModule(FakeArtifactKey artifactKey) {
            reloadableWorkspaceModules.add(artifactKey);
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
                    List.copyOf(reloadableWorkspaceModules),
                    Map.copyOf(removedResources));
        }
    }

    public static final class IncompatibleApplicationModelBuilder {
        public IncompatibleApplicationModelBuilder addDependency(FakeResolvedDependencyBuilder dependency) {
            return this;
        }

        public FakeApplicationModel build() {
            return new FakeApplicationModel(null, null, List.of(), List.of(), List.of(), List.of(), Map.of());
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
        private boolean workspaceModuleFlag;
        private boolean reloadable;
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

        public FakeResolvedDependencyBuilder setWorkspaceModule() {
            this.workspaceModuleFlag = true;
            return this;
        }

        public FakeResolvedDependencyBuilder setReloadable() {
            this.reloadable = true;
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

        boolean workspaceModuleFlag() {
            return workspaceModuleFlag;
        }

        boolean reloadable() {
            return reloadable;
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
            List<FakeArtifactKey> reloadableWorkspaceModules,
            Map<FakeArtifactKey, List<String>> removedResources) {
    }
}
