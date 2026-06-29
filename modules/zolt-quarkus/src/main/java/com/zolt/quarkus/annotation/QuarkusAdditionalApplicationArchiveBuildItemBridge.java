package com.zolt.quarkus.annotation;

import io.quarkus.builder.BuildChainBuilder;
import io.quarkus.builder.item.BuildItem;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

final class QuarkusAdditionalApplicationArchiveBuildItemBridge {
    private static final String ADDITIONAL_APPLICATION_ARCHIVE_BUILD_ITEM =
            "io.quarkus.deployment.builditem.AdditionalApplicationArchiveBuildItem";
    private static final String PATHS_COLLECTION = "io.quarkus.bootstrap.model.PathsCollection";
    private static final String PATH_COLLECTION = "io.quarkus.paths.PathCollection";

    private QuarkusAdditionalApplicationArchiveBuildItemBridge() {
    }

    static void addTestOutputArchive(BuildChainBuilder builder) {
        Optional<Path> testOutputDirectory = testOutputDirectory();
        Optional<Class<? extends BuildItem>> buildItemClass = resolve(builder);
        if (testOutputDirectory.isEmpty() || buildItemClass.isEmpty()) {
            ZoltQuarkusTestClassBeanCustomizer.writeDiagnostic(
                    "testOutputArchiveBuildItem.available=false",
                    "testOutputArchiveBuildItem.reason="
                            + (testOutputDirectory.isEmpty() ? "missing-test-output" : "missing-build-item"));
            return;
        }
        Class<? extends BuildItem> itemClass = buildItemClass.orElseThrow();
        Path testOutput = testOutputDirectory.orElseThrow();
        builder.addBuildStep(context -> {
                    ZoltQuarkusTestClassBeanCustomizer.writeDiagnostic(
                            "testOutputArchiveStep.executed=true",
                            "testOutputArchiveStep.path=" + testOutput);
                    context.produce(testOutputArchiveBuildItem(itemClass, testOutput));
                })
                .produces(itemClass)
                .build();
    }

    static Optional<Path> testOutputDirectory() {
        String value = System.getProperty(QuarkusAnnotationProgrammaticRunner.TEST_OUTPUT_DIRECTORY_PROPERTY, "");
        if (value.isBlank()) {
            return Optional.empty();
        }
        Path path = Path.of(value).toAbsolutePath().normalize();
        return Files.isDirectory(path) ? Optional.of(path) : Optional.empty();
    }

    @SuppressWarnings("unchecked")
    private static Optional<Class<? extends BuildItem>> resolve(BuildChainBuilder builder) {
        try {
            ClassLoader classLoader = ZoltQuarkusTestClassBeanCustomizer.buildChainClassLoader(builder);
            Class<?> buildItemClass = Class.forName(ADDITIONAL_APPLICATION_ARCHIVE_BUILD_ITEM, false, classLoader);
            if (!BuildItem.class.isAssignableFrom(buildItemClass)) {
                ZoltQuarkusTestClassBeanCustomizer.writeDiagnostic(
                        "testOutputArchiveBuildItem.available=false",
                        "testOutputArchiveBuildItem.reason=not-build-item",
                        "testOutputArchiveBuildItem.loader="
                                + ZoltQuarkusTestClassBeanCustomizer.classLoaderName(buildItemClass.getClassLoader()));
                return Optional.empty();
            }
            ZoltQuarkusTestClassBeanCustomizer.writeDiagnostic(
                    "testOutputArchiveBuildItem.available=true",
                    "testOutputArchiveBuildItem.loader="
                            + ZoltQuarkusTestClassBeanCustomizer.classLoaderName(buildItemClass.getClassLoader()));
            return Optional.of((Class<? extends BuildItem>) buildItemClass);
        } catch (ReflectiveOperationException | LinkageError exception) {
            ZoltQuarkusTestClassBeanCustomizer.writeDiagnostic(
                    "testOutputArchiveBuildItem.available=false",
                    "testOutputArchiveBuildItem.reason=" + exception.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    private static BuildItem testOutputArchiveBuildItem(
            Class<? extends BuildItem> buildItemClass,
            Path testOutputDirectory) {
        try {
            ClassLoader classLoader = buildItemClass.getClassLoader();
            Class<?> pathsCollectionClass = Class.forName(PATHS_COLLECTION, false, classLoader);
            Class<?> pathCollectionClass = Class.forName(PATH_COLLECTION, false, classLoader);
            Method of = pathsCollectionClass.getMethod("of", Path[].class);
            Object paths = of.invoke(null, (Object) new Path[] {testOutputDirectory});
            return buildItemClass.getConstructor(pathCollectionClass).newInstance(paths);
        } catch (ReflectiveOperationException | LinkageError exception) {
            throw new IllegalStateException(
                    "Could not produce Quarkus test-output application archive build item for "
                            + testOutputDirectory
                            + ".",
                    exception);
        }
    }
}
