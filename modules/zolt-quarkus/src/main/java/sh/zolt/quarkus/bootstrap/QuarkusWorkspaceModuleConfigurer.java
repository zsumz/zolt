package sh.zolt.quarkus.bootstrap;

import sh.zolt.quarkus.bootstrap.descriptor.QuarkusBootstrapDescriptor;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

final class QuarkusWorkspaceModuleConfigurer {
    void configure(
            Class<?> applicationModelBuilderClass,
            Class<?> resolvedDependencyBuilderClass,
            Class<?> artifactKeyClass,
            Object modelBuilder,
            Object appArtifactBuilder,
            QuarkusBootstrapDescriptor descriptor,
            QuarkusWorkspaceModuleInputs inputs)
            throws ReflectiveOperationException {
        Class<?> workspaceModuleIdClass = Class.forName("io.quarkus.bootstrap.workspace.WorkspaceModuleId");
        Class<?> workspaceModuleClass = Class.forName("io.quarkus.bootstrap.workspace.WorkspaceModule");
        Class<?> workspaceModuleMutableClass = Class.forName("io.quarkus.bootstrap.workspace.WorkspaceModule$Mutable");
        Class<?> sourceDirClass = Class.forName("io.quarkus.bootstrap.workspace.SourceDir");
        Class<?> artifactSourcesClass = Class.forName("io.quarkus.bootstrap.workspace.ArtifactSources");
        Object module = createModule(
                applicationModelBuilderClass,
                workspaceModuleIdClass,
                modelBuilder,
                descriptor,
                inputs);
        workspaceModuleMutableClass
                .getMethod("setBuildFile", Path.class)
                .invoke(module, inputs.projectDirectory().resolve("zolt.toml").toAbsolutePath().normalize());
        workspaceModuleMutableClass
                .getMethod("addArtifactSources", artifactSourcesClass)
                .invoke(module, mainSources(sourceDirClass, artifactSourcesClass, inputs));
        workspaceModuleMutableClass
                .getMethod("addArtifactSources", artifactSourcesClass)
                .invoke(module, testSources(sourceDirClass, artifactSourcesClass, inputs));
        workspaceModuleMutableClass
                .getMethod("setAdditionalTestClasspathElements", java.util.Collection.class)
                .invoke(module, additionalTestClasspathElements(inputs));
        resolvedDependencyBuilderClass
                .getMethod("setWorkspaceModule", workspaceModuleClass)
                .invoke(appArtifactBuilder, module);
        resolvedDependencyBuilderClass
                .getMethod("setWorkspaceModule")
                .invoke(appArtifactBuilder);
        resolvedDependencyBuilderClass
                .getMethod("setReloadable")
                .invoke(appArtifactBuilder);
        applicationModelBuilderClass
                .getMethod("addReloadableWorkspaceModule", artifactKeyClass)
                .invoke(modelBuilder, QuarkusApplicationModelFactory.artifactKey(
                        artifactKeyClass,
                        new QuarkusArtifactKey(
                                descriptor.applicationArtifact().packageId().groupId(),
                                descriptor.applicationArtifact().packageId().artifactId(),
                                Optional.empty(),
                                Optional.of(descriptor.applicationArtifact().type()))));
    }

    private static Object createModule(
            Class<?> applicationModelBuilderClass,
            Class<?> workspaceModuleIdClass,
            Object modelBuilder,
            QuarkusBootstrapDescriptor descriptor,
            QuarkusWorkspaceModuleInputs inputs)
            throws ReflectiveOperationException {
        Object moduleId = workspaceModuleIdClass
                .getMethod("of", String.class, String.class, String.class)
                .invoke(
                        null,
                        descriptor.applicationArtifact().packageId().groupId(),
                        descriptor.applicationArtifact().packageId().artifactId(),
                        descriptor.applicationArtifact().version());
        return applicationModelBuilderClass
                .getMethod("getOrCreateProjectModule", workspaceModuleIdClass, File.class, File.class)
                .invoke(
                        modelBuilder,
                        moduleId,
                        inputs.projectDirectory().toAbsolutePath().normalize().toFile(),
                        inputs.buildDirectory().toAbsolutePath().normalize().toFile());
    }

    private static List<String> additionalTestClasspathElements(QuarkusWorkspaceModuleInputs inputs) {
        return List.of(
                inputs.mainOutputDirectory().toAbsolutePath().normalize().toString(),
                inputs.testOutputDirectory().toAbsolutePath().normalize().toString());
    }

    private static Object mainSources(
            Class<?> sourceDirClass,
            Class<?> artifactSourcesClass,
            QuarkusWorkspaceModuleInputs inputs)
            throws ReflectiveOperationException {
        Object sources = sourceDir(sourceDirClass, inputs.mainSourceDirectory(), inputs.mainOutputDirectory());
        Object resources = sourceDir(sourceDirClass, inputs.mainResourceDirectory(), inputs.mainOutputDirectory());
        return artifactSourcesClass
                .getMethod("main", sourceDirClass, sourceDirClass)
                .invoke(null, sources, resources);
    }

    private static Object testSources(
            Class<?> sourceDirClass,
            Class<?> artifactSourcesClass,
            QuarkusWorkspaceModuleInputs inputs)
            throws ReflectiveOperationException {
        Object sources = sourceDir(sourceDirClass, inputs.testSourceDirectory(), inputs.testOutputDirectory());
        Object resources = sourceDir(sourceDirClass, inputs.testResourceDirectory(), inputs.testOutputDirectory());
        return artifactSourcesClass
                .getMethod("test", sourceDirClass, sourceDirClass)
                .invoke(null, sources, resources);
    }

    private static Object sourceDir(
            Class<?> sourceDirClass,
            Path sourceDirectory,
            Path outputDirectory)
            throws ReflectiveOperationException {
        return sourceDirClass
                .getMethod("of", Path.class, Path.class)
                .invoke(
                        null,
                        sourceDirectory.toAbsolutePath().normalize(),
                        outputDirectory.toAbsolutePath().normalize());
    }
}
