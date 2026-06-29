package com.zolt.quarkus.annotation;

import io.quarkus.builder.BuildChainBuilder;
import io.quarkus.builder.BuildStepBuilder;
import io.quarkus.builder.item.BuildItem;
import io.quarkus.deployment.builditem.TestProfileBuildItem;
import io.quarkus.test.junit.buildchain.TestBuildChainCustomizerProducer;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.jboss.jandex.Index;

public final class ZoltQuarkusTestClassBeanCustomizer implements TestBuildChainCustomizerProducer {
    private static final String TEST_CLASS_BEAN_BUILD_ITEM =
            "io.quarkus.deployment.builditem.TestClassBeanBuildItem";
    private static final String ADDITIONAL_BEAN_BUILD_ITEM =
            "io.quarkus.arc.deployment.AdditionalBeanBuildItem";
    private static final String APPLICATION_CLASS_PREDICATE_BUILD_ITEM =
            "io.quarkus.deployment.builditem.ApplicationClassPredicateBuildItem";
    private static final String TEST_CLASS_DEFAULT_SCOPE =
            "jakarta.enterprise.context.Dependent";
    private static final String DIAGNOSTIC_FILE_PROPERTY =
            "zolt.quarkus.test-class-bean-diagnostic-file";

    @Override
    public Consumer<BuildChainBuilder> produce(Index index) {
        List<String> testClasses = QuarkusTestProfileDiagnostic.testClasses(index);
        List<QuarkusTestProfileDiagnostic.TestClassProfile> testProfiles =
                QuarkusTestProfileDiagnostic.testClassProfiles(index);
        writeDiagnostic(
                "producer.invoked=true",
                "producer.loader=" + classLoaderName(getClass().getClassLoader()),
                "producer.indexClass=" + (index == null ? "<null>" : index.getClass().getName()),
                "producer.candidates=" + joined(testClasses),
                "producer.indexSelectedClasses="
                        + QuarkusSelectedTestIndexDiagnostic.formatProducerIndex(index, testClasses),
                "producer.testProfiles=" + QuarkusTestProfileDiagnostic.joinedProfiles(testProfiles));
        return builder -> {
            writeDiagnostic(
                    "consumer.accepted=true",
                    "consumer.builderClass=" + builder.getClass().getName(),
                    "consumer.candidates=" + joined(testClasses),
                    "consumer.testProfiles=" + QuarkusTestProfileDiagnostic.joinedProfiles(testProfiles));
            if (testClasses.isEmpty()) {
                return;
            }
            QuarkusAdditionalApplicationArchiveBuildItemBridge.addTestOutputArchive(builder);
            Optional<Class<? extends BuildItem>> applicationClassPredicateBuildItem =
                    applicationClassPredicateBuildItemClass(builder);
            applicationClassPredicateBuildItem.ifPresent(buildItemClass -> builder.addBuildStep(context -> {
                        writeDiagnostic(
                                "applicationClassPredicateStep.executed=true",
                                "applicationClassPredicateStep.buildItemLoader="
                                        + classLoaderName(buildItemClass.getClassLoader()),
                                "applicationClassPredicateStep.outputDirectories="
                                        + joinedPaths(ZoltQuarkusApplicationClassPredicate.normalizedOutputDirectories()));
                        context.produce(applicationClassPredicateBuildItem(buildItemClass));
                    })
                    .produces(buildItemClass)
                    .build());
            Optional<Class<? extends BuildItem>> additionalBeanBuildItem =
                    additionalBeanBuildItemClass(builder);
            Optional<Class<? extends BuildItem>> testClassBeanBuildItem =
                    testClassBeanBuildItemClass(builder);
            testClassBeanBuildItem.ifPresent(buildItemClass -> {
                BuildStepBuilder testClassBeanStep = builder.addBuildStep(context -> {
                        String activeProfile = activeProfile(context);
                        writeDiagnostic(
                                "buildStep.executed=true",
                                "buildStep.testClassBeanBuildItemLoader="
                                        + classLoaderName(buildItemClass.getClassLoader()),
                                "buildStep.activeProfile=" + activeProfile,
                                "buildStep.profileMatchesActiveProfile="
                                        + QuarkusTestProfileDiagnostic.joinedProfileMatches(
                                                testProfiles,
                                                activeProfile),
                                "buildStep.produced=" + joined(testClasses));
                        for (String testClass : testClasses) {
                            context.produce(testClassBeanBuildItem(buildItemClass, testClass));
                        }
                    });
                QuarkusOptionalBuildItemConsumes.optional(testClassBeanStep, TestProfileBuildItem.class)
                        .produces(buildItemClass)
                        .build();
            });
            additionalBeanBuildItem.ifPresent(buildItemClass -> {
                builder.addBuildStep(context -> {
                            BuildItem zoltAdditionalBeanItem = additionalBeanBuildItem(buildItemClass, testClasses);
                            writeDiagnostic(
                                    "additionalBeanProducerStep.executed=true",
                                    "additionalBeanProducerStep.produced=" + joined(testClasses));
                            context.produce(zoltAdditionalBeanItem);
                        })
                        .produces(buildItemClass)
                        .build();
            });
        };
    }

    @SuppressWarnings("unchecked")
    private static Optional<Class<? extends BuildItem>> testClassBeanBuildItemClass(BuildChainBuilder builder) {
        try {
            ClassLoader classLoader = buildChainClassLoader(builder);
            Class<?> buildItemClass = Class.forName(TEST_CLASS_BEAN_BUILD_ITEM, false, classLoader);
            if (!BuildItem.class.isAssignableFrom(buildItemClass)) {
                writeDiagnostic(
                        "testClassBeanBuildItem.available=false",
                        "testClassBeanBuildItem.reason=not-build-item",
                        "testClassBeanBuildItem.loader=" + classLoaderName(buildItemClass.getClassLoader()));
                return Optional.empty();
            }
            writeDiagnostic(
                    "testClassBeanBuildItem.available=true",
                    "testClassBeanBuildItem.loader=" + classLoaderName(buildItemClass.getClassLoader()));
            return Optional.of((Class<? extends BuildItem>) buildItemClass);
        } catch (ReflectiveOperationException | LinkageError exception) {
            writeDiagnostic(
                    "testClassBeanBuildItem.available=false",
                    "testClassBeanBuildItem.reason=" + exception.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    @SuppressWarnings("unchecked")
    private static Optional<Class<? extends BuildItem>> applicationClassPredicateBuildItemClass(BuildChainBuilder builder) {
        try {
            ClassLoader classLoader = buildChainClassLoader(builder);
            Class<?> buildItemClass = Class.forName(APPLICATION_CLASS_PREDICATE_BUILD_ITEM, false, classLoader);
            if (!BuildItem.class.isAssignableFrom(buildItemClass)) {
                writeDiagnostic(
                        "applicationClassPredicateBuildItem.available=false",
                        "applicationClassPredicateBuildItem.reason=not-build-item",
                        "applicationClassPredicateBuildItem.loader=" + classLoaderName(buildItemClass.getClassLoader()));
                return Optional.empty();
            }
            writeDiagnostic(
                    "applicationClassPredicateBuildItem.available=true",
                    "applicationClassPredicateBuildItem.loader=" + classLoaderName(buildItemClass.getClassLoader()));
            return Optional.of((Class<? extends BuildItem>) buildItemClass);
        } catch (ReflectiveOperationException | LinkageError exception) {
            writeDiagnostic(
                    "applicationClassPredicateBuildItem.available=false",
                    "applicationClassPredicateBuildItem.reason=" + exception.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    @SuppressWarnings("unchecked")
    private static Optional<Class<? extends BuildItem>> additionalBeanBuildItemClass(BuildChainBuilder builder) {
        try {
            ClassLoader classLoader = buildChainClassLoader(builder);
            Class<?> buildItemClass = Class.forName(ADDITIONAL_BEAN_BUILD_ITEM, false, classLoader);
            if (!BuildItem.class.isAssignableFrom(buildItemClass)) {
                writeDiagnostic(
                        "additionalBeanBuildItem.available=false",
                        "additionalBeanBuildItem.reason=not-build-item",
                        "additionalBeanBuildItem.loader=" + classLoaderName(buildItemClass.getClassLoader()));
                return Optional.empty();
            }
            writeDiagnostic(
                    "additionalBeanBuildItem.available=true",
                    "additionalBeanBuildItem.loader=" + classLoaderName(buildItemClass.getClassLoader()));
            return Optional.of((Class<? extends BuildItem>) buildItemClass);
        } catch (ReflectiveOperationException | LinkageError exception) {
            writeDiagnostic(
                    "additionalBeanBuildItem.available=false",
                    "additionalBeanBuildItem.reason=" + exception.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    static ClassLoader buildChainClassLoader(BuildChainBuilder builder) throws ReflectiveOperationException {
        Method getClassLoader = builder.getClass().getDeclaredMethod("getClassLoader");
        getClassLoader.setAccessible(true);
        Object classLoader = getClassLoader.invoke(builder);
        if (classLoader instanceof ClassLoader loader) {
            return loader;
        }
        return builder.getClass().getClassLoader();
    }

    private static BuildItem applicationClassPredicateBuildItem(Class<? extends BuildItem> buildItemClass) {
        try {
            return buildItemClass
                    .getConstructor(Predicate.class)
                    .newInstance((Predicate<String>) ZoltQuarkusApplicationClassPredicate::test);
        } catch (ReflectiveOperationException | LinkageError exception) {
            throw new IllegalStateException(
                    "Could not produce Quarkus application-class predicate build item for Zolt output classes.",
                    exception);
        }
    }

    private static BuildItem testClassBeanBuildItem(
            Class<? extends BuildItem> buildItemClass,
            String testClass) {
        try {
            return buildItemClass
                    .getConstructor(String.class)
                    .newInstance(testClass);
        } catch (ReflectiveOperationException | LinkageError exception) {
            throw new IllegalStateException(
                    "Could not produce Quarkus test-class bean build item for Zolt test class "
                            + testClass
                            + ".",
                    exception);
        }
    }

    private static BuildItem additionalBeanBuildItem(
            Class<? extends BuildItem> buildItemClass,
            List<String> testClasses) {
        try {
            Method builderMethod = buildItemClass.getMethod("builder");
            Object builder = builderMethod.invoke(null);
            QuarkusAdditionalBeanBuildItemBridge.markBuilderUnremovable(builder);
            QuarkusAdditionalBeanBuildItemBridge.setBuilderDefaultScope(builder, TEST_CLASS_DEFAULT_SCOPE);
            writeDiagnostic(
                    "additionalBeanBuildItem.unremovable=true",
                    "additionalBeanBuildItem.defaultScope=" + TEST_CLASS_DEFAULT_SCOPE);
            Method addBeanClass = builder.getClass().getMethod("addBeanClass", String.class);
            for (String testClass : testClasses) {
                addBeanClass.invoke(builder, testClass);
            }
            Method build = builder.getClass().getMethod("build");
            return (BuildItem) build.invoke(builder);
        } catch (ReflectiveOperationException | LinkageError exception) {
            throw new IllegalStateException(
                    "Could not produce Quarkus additional bean build item for Zolt test classes.",
                    exception);
        }
    }

    private static String joined(List<String> values) {
        return values.isEmpty() ? "<none>" : String.join(",", values);
    }

    private static String joinedPaths(List<Path> values) {
        return values.isEmpty()
                ? "<none>"
                : values.stream().map(Path::toString).collect(java.util.stream.Collectors.joining(","));
    }

    private static String activeProfile(io.quarkus.builder.BuildContext context) {
        try {
            TestProfileBuildItem testProfile = context.consume(TestProfileBuildItem.class);
            return testProfile == null ? "<none>" : testProfile.getTestProfileClassName();
        } catch (RuntimeException exception) {
            writeDiagnostic(
                    "testProfileBuildItem.consume=false",
                    "testProfileBuildItem.consumeReason=" + exception.getClass().getSimpleName());
            return "<unavailable>";
        }
    }

    static void writeDiagnostic(String... lines) {
        String diagnosticFile = System.getProperty(DIAGNOSTIC_FILE_PROPERTY, "");
        if (diagnosticFile.isBlank()) {
            return;
        }
        try {
            Path path = Path.of(diagnosticFile).toAbsolutePath().normalize();
            Files.createDirectories(path.getParent());
            StringBuilder content = new StringBuilder();
            for (String line : lines) {
                content.append(line).append(System.lineSeparator());
            }
            Files.writeString(
                    path,
                    content.toString(),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
        } catch (IOException | RuntimeException exception) {
            // Diagnostics must not change Quarkus test augmentation behavior.
        }
    }

    static String classLoaderName(ClassLoader classLoader) {
        if (classLoader == null) {
            return "<bootstrap>";
        }
        return classLoader.getClass().getName() + "@"
                + Integer.toHexString(System.identityHashCode(classLoader));
    }
}
