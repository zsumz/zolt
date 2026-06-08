package com.zolt.quarkus;

import com.zolt.resolve.DependencyScope;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

public final class QuarkusApplicationModelFactory {
    private final QuarkusApplicationModelApi api;

    public QuarkusApplicationModelFactory() {
        this(QuarkusApplicationModelApi.DEFAULT);
    }

    QuarkusApplicationModelFactory(QuarkusApplicationModelApi api) {
        if (api == null) {
            throw new QuarkusAugmentationException("Quarkus application model API is required.");
        }
        this.api = api;
    }

    public QuarkusApplicationModelHandle create(QuarkusBootstrapDescriptor descriptor) {
        if (descriptor == null) {
            throw new QuarkusAugmentationException("Quarkus bootstrap descriptor is required.");
        }

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
            applicationModelBuilderClass
                    .getMethod("setAppArtifact", resolvedDependencyBuilderClass)
                    .invoke(modelBuilder, appArtifact);
            setPlatformImports(applicationModelBuilderClass, modelBuilder, descriptor);
            for (QuarkusBootstrapDependency dependency : descriptor.bootstrapDependencies()) {
                Object dependencyBuilder = dependencyBuilder(
                        resolvedDependencyBuilderClass,
                        dependency,
                        dependency.scope(),
                        dependency.direct(),
                        dependency.path());
                applicationModelBuilderClass
                        .getMethod("addDependency", resolvedDependencyBuilderClass)
                        .invoke(modelBuilder, dependencyBuilder);
            }
            Object applicationModel = applicationModelBuilderClass.getMethod("build").invoke(modelBuilder);
            return new QuarkusApplicationModelHandle(
                    applicationModel,
                    applicationModel.getClass().getName(),
                    descriptor.bootstrapDependencies().size(),
                    runtimeDependencyCount(descriptor),
                    deploymentDependencyCount(descriptor));
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
        resolvedDependencyBuilderClass.getMethod("setDirect", boolean.class).invoke(builder, direct);
        if (scope == DependencyScope.QUARKUS_DEPLOYMENT) {
            resolvedDependencyBuilderClass.getMethod("setDeploymentCp").invoke(builder);
        } else {
            resolvedDependencyBuilderClass.getMethod("setRuntimeCp").invoke(builder);
        }
    }

    private static String quarkusScope(DependencyScope scope) {
        return scope == DependencyScope.RUNTIME || scope == DependencyScope.DEV ? "runtime" : "compile";
    }

    private static int runtimeDependencyCount(QuarkusBootstrapDescriptor descriptor) {
        return (int) descriptor.bootstrapDependencies().stream()
                .filter(dependency -> dependency.scope() != DependencyScope.QUARKUS_DEPLOYMENT)
                .count();
    }

    private static int deploymentDependencyCount(QuarkusBootstrapDescriptor descriptor) {
        return (int) descriptor.bootstrapDependencies().stream()
                .filter(dependency -> dependency.scope() == DependencyScope.QUARKUS_DEPLOYMENT)
                .count();
    }
}
