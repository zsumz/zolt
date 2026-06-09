package com.zolt.quarkus;

import com.zolt.resolve.DependencyScope;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Properties;

public final class QuarkusApplicationModelFactory {
    private final QuarkusApplicationModelApi api;
    private final QuarkusExtensionMetadataReader metadataReader;

    public QuarkusApplicationModelFactory() {
        this(QuarkusApplicationModelApi.DEFAULT, new QuarkusExtensionMetadataReader());
    }

    QuarkusApplicationModelFactory(QuarkusApplicationModelApi api) {
        this(api, new QuarkusExtensionMetadataReader());
    }

    QuarkusApplicationModelFactory(
            QuarkusApplicationModelApi api,
            QuarkusExtensionMetadataReader metadataReader) {
        if (api == null) {
            throw new QuarkusAugmentationException("Quarkus application model API is required.");
        }
        if (metadataReader == null) {
            throw new QuarkusAugmentationException("Quarkus extension metadata reader is required.");
        }
        this.api = api;
        this.metadataReader = metadataReader;
    }

    public QuarkusApplicationModelHandle create(QuarkusBootstrapDescriptor descriptor) {
        return create(descriptor, Optional.empty(), QuarkusApplicationModelOptions.DEFAULT);
    }

    public QuarkusApplicationModelHandle create(
            QuarkusBootstrapDescriptor descriptor,
            Optional<QuarkusWorkspaceModuleInputs> workspaceModuleInputs) {
        return create(descriptor, workspaceModuleInputs, QuarkusApplicationModelOptions.DEFAULT);
    }

    public QuarkusApplicationModelHandle create(
            QuarkusBootstrapDescriptor descriptor,
            Optional<QuarkusWorkspaceModuleInputs> workspaceModuleInputs,
            QuarkusApplicationModelOptions options) {
        if (descriptor == null) {
            throw new QuarkusAugmentationException("Quarkus bootstrap descriptor is required.");
        }
        workspaceModuleInputs = workspaceModuleInputs == null ? Optional.empty() : workspaceModuleInputs;
        options = options == null ? QuarkusApplicationModelOptions.DEFAULT : options;

        try {
            Class<?> applicationModelBuilderClass = Class.forName(api.applicationModelBuilderClass());
            Class<?> resolvedDependencyBuilderClass = Class.forName(api.resolvedDependencyBuilderClass());
            Object modelBuilder = applicationModelBuilderClass.getConstructor().newInstance();
            Object appArtifact = dependencyBuilder(
                    resolvedDependencyBuilderClass,
                    descriptor.applicationArtifact(),
                    DependencyScope.COMPILE,
                    true,
                    descriptor.applicationArtifact().path());
            setPlatformImports(applicationModelBuilderClass, modelBuilder, descriptor);
            Map<DependencyKey, DependencyBuilderState> dependencyBuilders = new LinkedHashMap<>();
            for (QuarkusBootstrapDependency dependency : descriptor.bootstrapDependencies()) {
                DependencyKey key = DependencyKey.from(dependency);
                DependencyBuilderState state = dependencyBuilders.get(key);
                if (state == null) {
                    state = new DependencyBuilderState(dependencyBuilder(
                            resolvedDependencyBuilderClass,
                            dependency,
                            dependency.scope(),
                            dependency.direct(),
                            dependency.path()));
                    dependencyBuilders.put(key, state);
                } else {
                    markDependency(resolvedDependencyBuilderClass, state.builder(), dependency.scope(), dependency.direct());
                }
                state.include(dependency.scope());
            }
            for (DependencyBuilderState state : dependencyBuilders.values()) {
                applicationModelBuilderClass
                        .getMethod("addDependency", resolvedDependencyBuilderClass)
                        .invoke(modelBuilder, state.builder());
            }
            if (workspaceModuleInputs.isPresent()) {
                setWorkspaceModule(
                        applicationModelBuilderClass,
                        resolvedDependencyBuilderClass,
                        modelBuilder,
                        appArtifact,
                        descriptor,
                        workspaceModuleInputs.orElseThrow());
            }
            applicationModelBuilderClass
                    .getMethod("setAppArtifact", resolvedDependencyBuilderClass)
                    .invoke(modelBuilder, appArtifact);
            setClassLoadingArtifacts(applicationModelBuilderClass, modelBuilder, descriptor, options);
            Object applicationModel = applicationModelBuilderClass.getMethod("build").invoke(modelBuilder);
            return new QuarkusApplicationModelHandle(
                    applicationModel,
                    applicationModel.getClass().getName(),
                    dependencyBuilders.size(),
                    runtimeDependencyCount(dependencyBuilders),
                    deploymentDependencyCount(dependencyBuilders));
        } catch (ClassNotFoundException exception) {
            throw new QuarkusAugmentationException(
                    "Quarkus application model classes are missing from the bootstrap worker classpath. "
                            + "Ensure quarkus-bootstrap-app-model is present and try again.",
                    exception);
        } catch (NoSuchMethodException exception) {
            throw new QuarkusAugmentationException(
                    "Quarkus application model API is incompatible with Zolt. Missing method "
                            + exception.getMessage()
                            + ". Update Zolt or use a supported Quarkus version.",
                    exception);
        } catch (InstantiationException | IllegalAccessException exception) {
            throw new QuarkusAugmentationException(
                    "Could not access Quarkus application model API. Check the Quarkus deployment classpath.",
                    exception);
        } catch (IOException exception) {
            throw new QuarkusAugmentationException(
                    "Could not read Quarkus platform properties. Run `zolt resolve`, then run `zolt build` again.",
                    exception);
        } catch (QuarkusMetadataException exception) {
            throw new QuarkusAugmentationException(exception.getMessage(), exception);
        } catch (InvocationTargetException exception) {
            throw new QuarkusAugmentationException(
                    "Quarkus application model creation failed. Check the Quarkus augmentation inputs.",
                    exception.getCause() == null ? exception : exception.getCause());
        } catch (ReflectiveOperationException exception) {
            throw new QuarkusAugmentationException(
                    "Could not create Quarkus application model. Check the Quarkus deployment classpath.",
                    exception);
        }
    }

    private static void setWorkspaceModule(
            Class<?> applicationModelBuilderClass,
            Class<?> resolvedDependencyBuilderClass,
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
        Object moduleId = workspaceModuleIdClass
                .getMethod("of", String.class, String.class, String.class)
                .invoke(
                        null,
                        descriptor.applicationArtifact().packageId().groupId(),
                        descriptor.applicationArtifact().packageId().artifactId(),
                        descriptor.applicationArtifact().version());
        Object module = applicationModelBuilderClass
                .getMethod("getOrCreateProjectModule", workspaceModuleIdClass, File.class, File.class)
                .invoke(
                        modelBuilder,
                        moduleId,
                        inputs.projectDirectory().toAbsolutePath().normalize().toFile(),
                        inputs.buildDirectory().toAbsolutePath().normalize().toFile());
        workspaceModuleMutableClass
                .getMethod("setBuildFile", Path.class)
                .invoke(module, inputs.projectDirectory().resolve("zolt.toml").toAbsolutePath().normalize());
        workspaceModuleMutableClass
                .getMethod("addArtifactSources", artifactSourcesClass)
                .invoke(module, mainSources(sourceDirClass, artifactSourcesClass, inputs));
        workspaceModuleMutableClass
                .getMethod("addArtifactSources", artifactSourcesClass)
                .invoke(module, testSources(sourceDirClass, artifactSourcesClass, inputs));
        resolvedDependencyBuilderClass
                .getMethod("setWorkspaceModule", workspaceModuleClass)
                .invoke(appArtifactBuilder, module);
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

    private void setClassLoadingArtifacts(
            Class<?> applicationModelBuilderClass,
            Object modelBuilder,
            QuarkusBootstrapDescriptor descriptor,
            QuarkusApplicationModelOptions options)
            throws ReflectiveOperationException {
        Set<QuarkusArtifactKey> parentFirstArtifacts = new LinkedHashSet<>(options.parentFirstArtifacts());
        Set<QuarkusArtifactKey> runnerParentFirstArtifacts = new LinkedHashSet<>(options.runnerParentFirstArtifacts());
        for (Path runtimeArtifact : descriptor.runtimeClasspath()) {
            if (!Files.isRegularFile(runtimeArtifact)) {
                continue;
            }
            Optional<QuarkusExtensionMetadata> metadata = metadataReader.readIfPresent(runtimeArtifact);
            if (metadata.isEmpty()) {
                continue;
            }
            parentFirstArtifacts.addAll(metadata.orElseThrow().parentFirstArtifacts());
            runnerParentFirstArtifacts.addAll(metadata.orElseThrow().runnerParentFirstArtifacts());
        }
        if (parentFirstArtifacts.isEmpty() && runnerParentFirstArtifacts.isEmpty()) {
            return;
        }
        Class<?> artifactKeyClass = Class.forName(api.artifactKeyClass());
        for (QuarkusArtifactKey artifactKey : parentFirstArtifacts) {
            applicationModelBuilderClass
                    .getMethod("addParentFirstArtifact", artifactKeyClass)
                    .invoke(modelBuilder, artifactKey(artifactKeyClass, artifactKey));
        }
        for (QuarkusArtifactKey artifactKey : runnerParentFirstArtifacts) {
            applicationModelBuilderClass
                    .getMethod("addRunnerParentFirstArtifact", artifactKeyClass)
                    .invoke(modelBuilder, artifactKey(artifactKeyClass, artifactKey));
        }
    }

    private static Object artifactKey(
            Class<?> artifactKeyClass,
            QuarkusArtifactKey artifactKey)
            throws ReflectiveOperationException {
        return artifactKeyClass
                .getMethod("of", String.class, String.class, String.class, String.class)
                .invoke(
                        null,
                        artifactKey.groupId(),
                        artifactKey.artifactId(),
                        artifactKey.classifier().orElse(""),
                        artifactKey.type().orElse("jar"));
    }

    private void setPlatformImports(
            Class<?> applicationModelBuilderClass,
            Object modelBuilder,
            QuarkusBootstrapDescriptor descriptor)
            throws ReflectiveOperationException, IOException {
        Map<String, String> platformProperties = platformProperties(descriptor);
        if (platformProperties.isEmpty()) {
            return;
        }
        Class<?> platformImportsClass = Class.forName(api.platformImportsClass());
        Class<?> platformImportsImplClass = Class.forName(api.platformImportsImplClass());
        Object platformImports = platformImportsImplClass.getConstructor().newInstance();
        platformImportsImplClass
                .getMethod("setPlatformProperties", Map.class)
                .invoke(platformImports, platformProperties);
        applicationModelBuilderClass
                .getMethod("setPlatformImports", platformImportsClass)
                .invoke(modelBuilder, platformImports);
    }

    private static Map<String, String> platformProperties(QuarkusBootstrapDescriptor descriptor) throws IOException {
        Map<String, String> values = new LinkedHashMap<>();
        for (Path propertiesFile : descriptor.platformPropertiesFiles()) {
            Properties properties = new Properties();
            try (var input = Files.newInputStream(propertiesFile)) {
                properties.load(input);
            }
            properties.stringPropertyNames().stream()
                    .sorted()
                    .forEach(key -> values.put(key, properties.getProperty(key)));
        }
        return values;
    }

    private static Object dependencyBuilder(
            Class<?> resolvedDependencyBuilderClass,
            QuarkusApplicationArtifact artifact,
            DependencyScope scope,
            boolean direct,
            Path path)
            throws ReflectiveOperationException {
        Object builder = resolvedDependencyBuilderClass.getMethod("newInstance").invoke(null);
        setDependencyFields(
                resolvedDependencyBuilderClass,
                builder,
                artifact.packageId().groupId(),
                artifact.packageId().artifactId(),
                artifact.version(),
                artifact.classifier(),
                artifact.type(),
                scope,
                direct,
                path);
        return builder;
    }

    private static Object dependencyBuilder(
            Class<?> resolvedDependencyBuilderClass,
            QuarkusBootstrapDependency dependency,
            DependencyScope scope,
            boolean direct,
            Path path)
            throws ReflectiveOperationException {
        Object builder = resolvedDependencyBuilderClass.getMethod("newInstance").invoke(null);
        setDependencyFields(
                resolvedDependencyBuilderClass,
                builder,
                dependency.packageId().groupId(),
                dependency.packageId().artifactId(),
                dependency.version(),
                dependency.classifier(),
                dependency.type(),
                scope,
                direct,
                path);
        return builder;
    }

    private static void setDependencyFields(
            Class<?> resolvedDependencyBuilderClass,
            Object builder,
            String groupId,
            String artifactId,
            String version,
            String classifier,
            String type,
            DependencyScope scope,
            boolean direct,
            Path path)
            throws ReflectiveOperationException {
        resolvedDependencyBuilderClass.getMethod("setGroupId", String.class).invoke(builder, groupId);
        resolvedDependencyBuilderClass.getMethod("setArtifactId", String.class).invoke(builder, artifactId);
        resolvedDependencyBuilderClass.getMethod("setVersion", String.class).invoke(builder, version);
        resolvedDependencyBuilderClass.getMethod("setClassifier", String.class).invoke(builder, classifier);
        resolvedDependencyBuilderClass.getMethod("setType", String.class).invoke(builder, type);
        resolvedDependencyBuilderClass.getMethod("setScope", String.class).invoke(builder, quarkusScope(scope));
        resolvedDependencyBuilderClass.getMethod("setResolvedPath", Path.class).invoke(builder, path);
        markDependency(resolvedDependencyBuilderClass, builder, scope, direct);
    }

    private static void markDependency(
            Class<?> resolvedDependencyBuilderClass,
            Object builder,
            DependencyScope scope,
            boolean direct)
            throws ReflectiveOperationException {
        if (direct) {
            resolvedDependencyBuilderClass.getMethod("setDirect", boolean.class).invoke(builder, true);
        }
        if (scope == DependencyScope.QUARKUS_DEPLOYMENT) {
            resolvedDependencyBuilderClass.getMethod("setDeploymentCp").invoke(builder);
        } else {
            resolvedDependencyBuilderClass.getMethod("setRuntimeCp").invoke(builder);
        }
    }

    private static String quarkusScope(DependencyScope scope) {
        return scope == DependencyScope.RUNTIME || scope == DependencyScope.DEV ? "runtime" : "compile";
    }

    private static int runtimeDependencyCount(Map<DependencyKey, DependencyBuilderState> dependencies) {
        return (int) dependencies.values().stream()
                .filter(DependencyBuilderState::runtimeClasspath)
                .count();
    }

    private static int deploymentDependencyCount(Map<DependencyKey, DependencyBuilderState> dependencies) {
        return (int) dependencies.values().stream()
                .filter(DependencyBuilderState::deploymentClasspath)
                .count();
    }

    private record DependencyKey(
            String groupId,
            String artifactId,
            String classifier,
            String type) {
        static DependencyKey from(QuarkusBootstrapDependency dependency) {
            return new DependencyKey(
                    dependency.packageId().groupId(),
                    dependency.packageId().artifactId(),
                    dependency.classifier(),
                    dependency.type());
        }
    }

    private static final class DependencyBuilderState {
        private final Object builder;
        private boolean runtimeClasspath;
        private boolean deploymentClasspath;

        DependencyBuilderState(Object builder) {
            this.builder = builder;
        }

        Object builder() {
            return builder;
        }

        void include(DependencyScope scope) {
            if (scope == DependencyScope.QUARKUS_DEPLOYMENT) {
                deploymentClasspath = true;
            } else {
                runtimeClasspath = true;
            }
        }

        boolean runtimeClasspath() {
            return runtimeClasspath;
        }

        boolean deploymentClasspath() {
            return deploymentClasspath;
        }
    }
}
